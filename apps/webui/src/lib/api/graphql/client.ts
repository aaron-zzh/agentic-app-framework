/**
 * GraphQL API 客户端。
 */

import { GraphqlClient } from "../api-client"
import { API_ORIGIN } from "../config"

export const graphqlApi = new GraphqlClient(`${API_ORIGIN}/graphql`)
