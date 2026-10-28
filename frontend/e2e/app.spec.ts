import { expect, test, type Page } from '@playwright/test'

const PASSWORD = 'Passw0rd!'

async function login(page: Page, email: string) {
  // pin English so text assertions are stable regardless of the machine's locale
  await page.addInitScript(() => localStorage.setItem('orderpilot.lang', 'en'))
  await page.goto('/login')
  await page.getByTestId('login-email').fill(email)
  await page.getByTestId('login-password').fill(PASSWORD)
  await page.getByTestId('login-submit').click()
  await expect(page.getByTestId('user-menu')).toBeVisible()
}

test('admin signs in and sees live dashboard KPIs', async ({ page }) => {
  await login(page, 'admin@acme.test')
  await expect(page).toHaveURL(/\/dashboard/)
  const revenue = page.getByTestId('kpi-revenue')
  await expect(revenue).toContainText('Revenue')
  await expect(revenue).toContainText('$')
  await expect(page.getByText('Top products')).toBeVisible()
  await expect(page.getByText('Restock soon')).toBeVisible()
})

test('staff creates an order, pays it and ships it', async ({ page }) => {
  await login(page, 'staff@acme.test')
  await page.goto('/orders')
  await page.getByTestId('new-order').click()

  const customer = `E2E Customer ${Date.now()}`
  await page.getByTestId('order-customer').fill(customer)
  await page.getByTestId('order-line-product-0').click()
  await page.locator('.el-select-dropdown__item:not(.is-disabled)').filter({ hasText: 'Merino Base Layer Top' }).click()
  await page.getByTestId('order-submit').click()

  const drawer = page.getByTestId('order-drawer')
  await expect(drawer.getByTestId('order-status')).toHaveText(/Awaiting payment/)
  await drawer.getByTestId('pay-button').click()
  await expect(drawer.getByTestId('order-status')).toHaveText(/Paid/)
  await expect(drawer.locator('[data-status="SUCCEEDED"]')).toBeVisible()

  await drawer.getByTestId('ship-button').click()
  await expect(drawer.getByTestId('order-status')).toHaveText(/Shipped/)

  // the order shows up in the list
  await page.keyboard.press('Escape')
  await page.getByTestId('order-search').fill(customer)
  await expect(page.getByTestId('orders-table')).toContainText(customer)
})

test('viewer gets a read-only workspace', async ({ page }) => {
  await login(page, 'viewer@acme.test')
  await page.goto('/orders')
  await expect(page.getByTestId('orders-table')).toBeVisible()
  await expect(page.getByTestId('new-order')).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Team' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'AI Copilot' })).toHaveCount(0)
  await page.goto('/team')
  await expect(page.getByText("You don't have access to this page")).toBeVisible()
})

test('tenants are isolated in the UI', async ({ page }) => {
  await login(page, 'admin@nova.test')
  await page.goto('/products')
  await expect(page.getByText('Pulse ANC Headphones')).toBeVisible()
  await expect(page.getByText('Alpine 2P Ultralight Tent')).toHaveCount(0)
})

test('AI copilot answers from live data with tool calls', async ({ page }) => {
  test.setTimeout(90_000) // a real LLM (DeepSeek) may take a while
  await login(page, 'manager@acme.test')
  await page.goto('/assistant')
  await page.getByTestId('ai-input').fill('Which products need restocking?')
  await page.getByTestId('ai-send').click()
  // works in both modes: the offline analyst and the LLM both answer through the server-side tools
  const answer = page.locator('.msg.assistant .bubble').last()
  await expect(answer.locator('.tools')).toBeVisible({ timeout: 60_000 })
  await expect(answer.locator('.markdown')).not.toBeEmpty()
})

test('switches between English and Chinese', async ({ page }) => {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('init')) {
      localStorage.setItem('orderpilot.lang', 'en')
      sessionStorage.setItem('init', '1')
    }
  })
  await page.goto('/login')
  await expect(page.getByTestId('login-submit')).toHaveText('Sign in')
  await page.getByTestId('lang-zh').click()
  await expect(page.getByTestId('login-submit')).toHaveText('登录')

  await page.getByTestId('login-submit').click()
  await expect(page.getByRole('heading', { name: '数据看板' })).toBeVisible()
  await expect(page.getByTestId('kpi-revenue')).toContainText('营收')
  await expect(page).toHaveTitle(/数据看板/)

  // the choice survives a reload
  await page.reload()
  await expect(page.getByRole('heading', { name: '数据看板' })).toBeVisible()

  await page.getByTestId('lang-en').click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
})
