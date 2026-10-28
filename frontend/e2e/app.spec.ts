import { expect, test, type Page } from '@playwright/test'

const PASSWORD = 'Passw0rd!'

async function login(page: Page, email: string) {
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
  await expect(drawer.getByText('SUCCEEDED')).toBeVisible()

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
  await login(page, 'manager@acme.test')
  await page.goto('/assistant')
  await page.getByTestId('ai-input').fill('Which products need restocking?')
  await page.getByTestId('ai-send').click()
  await expect(page.getByText('getLowStockProducts()')).toBeVisible()
  await expect(page.locator('.markdown table')).toBeVisible()
})
