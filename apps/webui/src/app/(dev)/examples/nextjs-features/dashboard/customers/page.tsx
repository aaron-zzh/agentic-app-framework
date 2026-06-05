import CustomersTable from "../../_components/customers/table"
import { fetchCustomers } from "../../_data/mock"

export default async function CustomersPage({
  searchParams
}: {
  searchParams: Promise<{ query?: string }>
}) {
  const { query } = await searchParams
  const customers = await fetchCustomers(query)
  return <CustomersTable customers={customers} />
}
