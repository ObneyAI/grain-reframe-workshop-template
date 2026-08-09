import { expect, test, type ConsoleMessage, type Page } from "@playwright/test"
import { readFile } from "node:fs/promises"

async function latestVerificationUrl(email: string, expectedCount: number) {
  const captureFile = process.env.PLAYWRIGHT_EMAIL_CAPTURE_FILE
  if (!captureFile) throw new Error("PLAYWRIGHT_EMAIL_CAPTURE_FILE is required")

  let latestUrl: string | undefined
  await expect
    .poll(async () => {
      const contents = await readFile(captureFile, "utf8")
      const urls = contents
        .split("\n")
        .filter((line) => line.includes(email))
        .flatMap((line) => [...line.matchAll(/href=\\"([^"\\]+verification-token=[^"\\]+)\\"/g)])
        .map((match) => match[1])
      latestUrl = urls.at(-1)
      return urls.length
    }, { message: `wait for verification email ${expectedCount}` })
    .toBeGreaterThanOrEqual(expectedCount)
  if (!latestUrl) throw new Error(`Verification email ${expectedCount} had no URL`)
  return latestUrl
}

function collectBrowserFailures(page: Page) {
  const failures: string[] = []
  const expectedResponses = new Map<string, number>()
  const responseKey = (method: string, pathname: string, status: number) =>
    `${method} ${pathname} ${status}`

  const allowNextResponse = (method: string, pathname: string, status: number) => {
    const key = responseKey(method, pathname, status)
    expectedResponses.set(key, (expectedResponses.get(key) ?? 0) + 1)
  }

  const recordConsoleFailure = (message: ConsoleMessage) => {
    const expectedHttpStatusMessage =
      /^Failed to load resource: the server responded with a status of (403|404|409)/.test(
        message.text(),
      )
    if (["error", "warning"].includes(message.type()) && !expectedHttpStatusMessage) {
      failures.push(`console.${message.type()}: ${message.text()}`)
    }
  }

  page.on("console", recordConsoleFailure)
  page.on("pageerror", (error) => failures.push(`pageerror: ${error.message}`))
  page.on("requestfailed", (request) => {
    failures.push(`requestfailed: ${request.method()} ${request.url()} ${request.failure()?.errorText}`)
  })
  page.on("response", (response) => {
    const url = new URL(response.url())
    const key = responseKey(response.request().method(), url.pathname, response.status())
    const expectedCount = expectedResponses.get(key) ?? 0
    const expectedAnonymousSession = url.pathname === "/query" && response.status() === 403
    const expectedNotFound = url.pathname === "/not-a-real-page" && response.status() === 404
    if (expectedCount > 0) {
      expectedResponses.set(key, expectedCount - 1)
    } else if (response.status() >= 400 && !expectedAnonymousSession && !expectedNotFound) {
      failures.push(`http.${response.status()}: ${response.request().method()} ${url.pathname}`)
    }
  })

  return { failures, allowNextResponse }
}

test("the clone-ready browser contract works without console failures", async ({ page, request }) => {
  test.setTimeout(60_000)
  const { failures, allowNextResponse } = collectBrowserFailures(page)
  const email = `browser-${Date.now()}@example.test`
  const password = "Starter123!"

  const health = await request.get("/healthcheck")
  expect(health.status()).toBe(200)
  expect(await health.text()).toBe("OK")

  await page.goto("/")
  await expect(page).toHaveURL(/\/auth\/sign-in\?return-to=%2F$/)
  await expect(page.getByRole("heading", { name: "Welcome back" })).toBeVisible()
  await expect(page.locator('meta[name="app-locale"]')).toHaveAttribute("content", "en-US")
  await expect(page.locator('meta[name="app-time-zone"]')).toHaveAttribute("content", "UTC")

  const protectedPath = "/examples/routes?record-id=browser-record&tab=history"
  await page.goto(protectedPath)
  await expect(page).toHaveURL(
    /\/auth\/sign-in\?return-to=%2Fexamples%2Froutes%3Frecord-id%3Dbrowser-record%26tab%3Dhistory$/,
  )

  await page.goto("/auth/sign-up")
  await page.reload()
  await expect(page.getByRole("heading", { name: "Create your account" })).toBeVisible()
  await page.locator("#sign-up-email").fill(email)
  await page.locator("#sign-up-password").fill(password)
  await page.locator("#sign-up-confirm-password").fill(password)
  await page.getByRole("button", { name: "Create account" }).click()
  await expect(page.getByText("Account created. Check your email to verify it.")).toBeVisible()

  await page.getByRole("button", { name: "Resend verification email" }).click()
  await expect(
    page.getByText("If an unverified account exists, a new verification email has been sent."),
  ).toBeVisible()
  const verificationUrl = await latestVerificationUrl(email, 2)

  allowNextResponse("POST", "/command", 409)
  await page.getByRole("button", { name: "Create account" }).click()
  await expect(page.locator("#sign-up-email-error")).toHaveText(
    "An account already exists for this email.",
  )
  await expect(page.locator("#sign-up-email")).toBeFocused()

  await page.goto(`/auth/sign-in?return-to=${encodeURIComponent(protectedPath)}`)
  await page.locator("#sign-in-email").fill(email)
  await page.locator("#sign-in-password").fill(password)
  allowNextResponse("POST", "/command", 403)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page.getByText("Verify your email before signing in.")).toBeVisible()
  await expect(page).toHaveURL(/\/auth\/sign-in/)

  await page.goto(verificationUrl)
  await expect(page.getByText("Email verified. You can sign in.")).toBeVisible()

  await page.goto(`/auth/sign-in?return-to=${encodeURIComponent(protectedPath)}`)
  await page.locator("#sign-in-email").fill(email)
  await page.locator("#sign-in-password").fill(password)
  await page.getByRole("button", { name: "Sign in" }).click()
  await expect(page).toHaveURL(new RegExp(`${protectedPath.replace("?", "\\?")}$`))
  await expect(page.getByRole("heading", { name: "Query-string route" })).toBeVisible()
  await expect(page.getByText("browser-record")).toBeVisible()
  await expect(page.getByText("history")).toBeVisible()

  await page.goto("/")
  await expect(page.getByRole("heading", { name: "A clean Grain canvas" })).toBeVisible()
  await expect(page.getByText(email, { exact: true }).first()).toBeVisible()

  await page.goto("/examples/questionnaire")
  await expect(page.getByRole("heading", { name: "Questionnaire bridge" })).toBeVisible()
  await expect(page.locator('[data-slot="questionnaire-progress"]')).toBeVisible()
  await expect(page.getByText("What should this Grain app prove first?")).toBeVisible()

  const customerName = `Northstar ${Date.now()}`
  await page.goto("/examples/customer-workbench?sort=name-asc")
  await expect(page.getByRole("heading", { name: "Customer workbench" })).toBeVisible()
  await expect(page.getByText("No customers yet")).toBeVisible()
  await page.getByRole("button", { name: "New customer" }).click()
  await page.getByLabel("Name").fill(customerName)
  await page.getByLabel("Email").fill(`customer-${Date.now()}@example.test`)
  await page.getByRole("button", { name: "Create customer" }).click()
  await expect(page).toHaveURL(
    /\/examples\/customer-workbench\?record-id=[^&]+&sort=name-asc&tab=summary$/,
  )
  await expect(page.getByTestId("customer-row").getByText(customerName)).toBeVisible()
  const detailSheet = page.locator('[data-slot="sheet-content"]')
  await expect(detailSheet.getByText(customerName)).toBeVisible()
  await detailSheet.getByRole("button", { name: "Change status" }).click()
  await page.getByRole("menuitem", { name: "Active", exact: true }).click()
  await expect(page.getByText("Status updated")).toBeVisible()
  await detailSheet.getByRole("tab", { name: "Activity" }).click()
  await expect(detailSheet.getByText("Status changed")).toBeVisible()
  const customerUrl = page.url()
  await page.reload()
  await expect(page).toHaveURL(customerUrl)
  await expect(detailSheet.getByText(customerName)).toBeVisible()

  const notFoundResponse = await page.goto("/not-a-real-page")
  expect(notFoundResponse?.status()).toBe(404)
  await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible()

  expect(failures, failures.join("\n")).toEqual([])
})
