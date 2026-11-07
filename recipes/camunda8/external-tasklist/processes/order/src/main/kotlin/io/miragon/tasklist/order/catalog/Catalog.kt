package io.miragon.tasklist.order.catalog

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal

data class CatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val price: BigDecimal,
)

/** The items the MiraVelo shop sells. */
@Repository
class Catalog(private val jdbc: JdbcClient) {

    fun items(): List<CatalogItem> =
        jdbc.sql("SELECT id, name, description, price FROM items ORDER BY name")
            .query { rs, _ ->
                CatalogItem(rs.getString("id"), rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"))
            }
            .list()
}
