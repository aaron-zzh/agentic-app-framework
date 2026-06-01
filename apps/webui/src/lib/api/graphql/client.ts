/**
 * GraphQL API 客户端。
 */

import { API_ORIGIN } from "../config"
import { GraphqlClient } from "../api-client"

export const graphqlApi = new GraphqlClient(`${API_ORIGIN}/graphql`)

