import { expect, test, type Page } from "@playwright/test";

function uniqueEmail(prefix: string): string {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  return `${prefix}.${suffix}@example.com`;
}

async function registerUser(
  page: Page,
  username: string,
  email: string,
  password: string
) {
  await page.goto("/register");
  const inputs = page.locator("form input");
  await inputs.nth(0).fill(username);
  await inputs.nth(1).fill(email);
  await inputs.nth(2).fill(password);
  await inputs.nth(3).fill(password);
  await page.getByRole("button", { name: "Zarejestruj" }).click();
  await expect(page).toHaveURL(/\/login$/);
}

async function loginUser(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill(password);
  await page.getByRole("button", { name: "Zaloguj" }).click();
  await expect(page).toHaveURL(/\/transactions$/);
  await expect(page.getByRole("button", { name: "Wyloguj" })).toBeVisible();
}

test.describe("Budget Split E2E", () => {
  test("registers, logs in, adds transactions, and updates balance", async ({
    page,
  }) => {
    const email = uniqueEmail("budget.user");
    const password = "Password123!";

    await registerUser(page, "BudgetUser", email, password);
    await loginUser(page, email, password);

    // Add an income transaction
    await page.locator("nav").getByRole("link", { name: "Dodaj Transakcję" }).click();
    await expect(page).toHaveURL(/\/add-transaction$/);

    await page.getByPlaceholder("Kwota").fill("1500");
    await page.locator("form select").selectOption("INCOME");
    await page.getByPlaceholder("np. jedzenie, transport").fill("salary");
    await page
      .getByPlaceholder("Dodatkowe informacje (opcjonalnie)")
      .fill("Monthly salary");
    await page.getByRole("button", { name: "Dodaj" }).click();

    await expect(page.getByText("1500.00 zł").first()).toBeVisible();

    // Add an expense transaction
    await page.getByPlaceholder("Kwota").fill("420.50");
    await page.locator("form select").selectOption("EXPENSE");
    await page.getByPlaceholder("np. jedzenie, transport").fill("groceries");
    await page
      .getByPlaceholder("Dodatkowe informacje (opcjonalnie)")
      .fill("Weekly shopping");
    await page.getByRole("button", { name: "Dodaj" }).click();

    // Verify updated balance bar totals (1500.00 - 420.50 = 1079.50)
    await expect(page.getByText("1079.50 zł")).toBeVisible();
    await expect(page.getByText("420.50 zł")).toBeVisible();

    // Verify both entries are listed in the transactions table
    await page.locator("nav").getByRole("link", { name: "Lista Transakcji" }).click();
    await expect(page).toHaveURL(/\/transactions$/);
    await expect(page.getByText("Monthly salary")).toBeVisible();
    await expect(page.getByText("Weekly shopping")).toBeVisible();
  });

  test("splits a group expense and completes debt settlement between members", async ({
    page,
  }) => {
    const ownerEmail = uniqueEmail("group.owner");
    const memberEmail = uniqueEmail("group.member");
    const password = "Password123!";

    await registerUser(page, "GroupOwner", ownerEmail, password);
    await registerUser(page, "GroupMember", memberEmail, password);

    // Owner creates a group, invites member, and logs a shared expense
    await loginUser(page, ownerEmail, password);
    await page.locator("nav").getByRole("link", { name: "Grupy" }).click();
    await expect(page).toHaveURL(/\/groups$/);

    await page.getByPlaceholder("Nazwa grupy").fill("Weekend Trip");
    await page.getByRole("button", { name: "Utwórz Grupę" }).click();
    await expect(
      page.getByRole("heading", { name: "Członkowie grupy: Weekend Trip" })
    ).toBeVisible();

    await page.getByPlaceholder("Email użytkownika").fill(memberEmail);
    await page.getByRole("button", { name: "Dodaj członka" }).click();
    await expect(page.getByText(memberEmail).first()).toBeVisible();

    const groupExpenseForm = page
      .locator("form")
      .filter({ hasText: "Uczestnicy transakcji" });
    await groupExpenseForm.getByPlaceholder("Tytuł").fill("Cabin Rental");
    await groupExpenseForm.getByPlaceholder("Kwota").fill("200");
    await groupExpenseForm.getByRole("button", { name: "Dodaj" }).click();

    await expect(page.getByText("Cabin Rental")).toBeVisible();
    await expect(page.getByText("100.00 zł")).toBeVisible();
    await expect(page.getByText("Nieopłacony")).toBeVisible();

    // Member logs in and marks their share as paid
    await page.getByRole("button", { name: "Wyloguj" }).click();
    await loginUser(page, memberEmail, password);
    await page.locator("nav").getByRole("link", { name: "Grupy" }).click();
    await page.locator("li", { hasText: "Weekend Trip" }).click();

    await expect(page.getByText("Cabin Rental")).toBeVisible();
    await page.getByRole("button", { name: "Oznacz jako opłacony" }).click();
    await expect(page.getByText("Oczekuje na potwierdzenie")).toBeVisible();

    // Owner logs back in and confirms the settlement
    await page.getByRole("button", { name: "Wyloguj" }).click();
    await loginUser(page, ownerEmail, password);
    await page.locator("nav").getByRole("link", { name: "Grupy" }).click();
    await page.locator("li", { hasText: "Weekend Trip" }).click();

    await page.getByRole("button", { name: "Potwierdź spłatę" }).click();
    await expect(page.getByText("Spłata potwierdzona")).toBeVisible();
  });
});
