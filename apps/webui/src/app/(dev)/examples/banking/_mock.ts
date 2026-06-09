/** Banking 示例 Mock 数据 */

export const mockCreditCards = [
  {
    id: "card-1",
    cardType: "visa",
    balance: 23432.03,
    cardHolder: "Jaydon Frankie",
    cardNumber: "**** **** **** 3640",
    cardValid: "11/22"
  },
  {
    id: "card-2",
    cardType: "mastercard",
    balance: 18900.23,
    cardHolder: "Airi Satou",
    cardNumber: "**** **** **** 8864",
    cardValid: "08/25"
  },
  {
    id: "card-3",
    cardType: "visa",
    balance: 9120.5,
    cardHolder: "Donna Snider",
    cardNumber: "**** **** **** 7225",
    cardValid: "03/27"
  }
]

export const mockContacts = [
  { id: "c1", name: "Soren Durham", email: "soren@example.com", avatarUrl: "" },
  { id: "c2", name: "Reece Chung", email: "reece@example.com", avatarUrl: "" },
  { id: "c3", name: "Jayvon Hull", email: "jayvon@example.com", avatarUrl: "" },
  { id: "c4", name: "Cristopher Cardenas", email: "cristopher@example.com", avatarUrl: "" },
  { id: "c5", name: "Melanie Noble", email: "melanie@example.com", avatarUrl: "" }
]

export const mockTransactions = [
  {
    id: "t1",
    type: "Income",
    status: "completed",
    amount: 500,
    message: "Salary payment",
    category: "Income",
    date: "2024-01-15T10:30:00",
    name: "Company Inc",
    avatarUrl: ""
  },
  {
    id: "t2",
    type: "Expenses",
    status: "completed",
    amount: -120,
    message: "Grocery shopping",
    category: "Supermarket",
    date: "2024-01-14T14:20:00",
    name: null,
    avatarUrl: ""
  },
  {
    id: "t3",
    type: "Expenses",
    status: "progress",
    amount: -45,
    message: "Fast food order",
    category: "Fast food",
    date: "2024-01-13T12:00:00",
    name: null,
    avatarUrl: ""
  },
  {
    id: "t4",
    type: "Income",
    status: "completed",
    amount: 1200,
    message: "Freelance project",
    category: "Income",
    date: "2024-01-12T09:00:00",
    name: "Client A",
    avatarUrl: ""
  },
  {
    id: "t5",
    type: "Expenses",
    status: "failed",
    amount: -80,
    message: "Gym membership",
    category: "Fitness",
    date: "2024-01-11T08:00:00",
    name: null,
    avatarUrl: ""
  }
]

export const mockBalanceStatistics = {
  series: [
    {
      name: "Weekly",
      categories: ["Week 1", "Week 2", "Week 3", "Week 4", "Week 5"],
      data: [
        { name: "Income", data: [24, 41, 35, 151, 49] },
        { name: "Savings", data: [24, 56, 77, 88, 99] },
        { name: "Expenses", data: [40, 34, 77, 88, 99] }
      ]
    },
    {
      name: "Monthly",
      categories: ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep"],
      data: [
        { name: "Income", data: [83, 112, 119, 88, 103, 112, 114, 108, 93] },
        { name: "Savings", data: [46, 46, 43, 58, 40, 59, 54, 42, 51] },
        { name: "Expenses", data: [25, 40, 38, 35, 20, 32, 27, 40, 21] }
      ]
    },
    {
      name: "Yearly",
      categories: ["2019", "2020", "2021", "2022", "2023", "2024"],
      data: [
        { name: "Income", data: [76, 42, 29, 41, 27, 96] },
        { name: "Savings", data: [46, 44, 24, 43, 44, 43] },
        { name: "Expenses", data: [23, 22, 37, 38, 32, 25] }
      ]
    }
  ]
}

export const mockExpensesCategories = [
  { label: "Entertainment", value: 22, icon: "gamepad-2" },
  { label: "Fuel", value: 18, icon: "fuel" },
  { label: "Fast food", value: 16, icon: "utensils" },
  { label: "Cafe", value: 17, icon: "coffee" },
  { label: "Connection", value: 14, icon: "smartphone" },
  { label: "Healthcare", value: 22, icon: "heart-pulse" },
  { label: "Fitness", value: 10, icon: "dumbbell" },
  { label: "Supermarket", value: 21, icon: "shopping-cart" }
]
