package com.beeftechlabs.repository.elastic

data class Query(
    var index: String = "",
    val query: MutableList<QueryField> = mutableListOf(),
    var sort: QuerySort = QuerySort(),
    var pageSize: Int = 20
) {
    fun initQueryField(field: QueryField, init: QueryField.() -> Unit) {
        field.init()
        query.add(field)
    }

    fun hasSort() = sort.field.isNotEmpty()
}

data class QueryField(
    val type: QueryFieldType,
    var name: String = "",
    var value: String = "",
    val children: MutableList<QueryField> = mutableListOf()
) {
    fun initQueryField(field: QueryField, init: QueryField.() -> Unit) {
        field.init()
        children.add(field)
    }
}

data class QuerySort(
    var field: String = "",
    var ascending: Boolean = true
)

enum class QueryFieldType { Term, Prefix, Filter, Should, Must, Bool }

fun elasticQuery(init: Query.() -> Unit): Query {
    val query = Query()
    query.init()
    return query
}

private fun Query.field(type: QueryFieldType, init: QueryField.() -> Unit) = initQueryField(QueryField(type), init)
fun Query.term(init: QueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun Query.prefix(init: QueryField.() -> Unit) = field(QueryFieldType.Prefix, init)
fun Query.filter(init: QueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun Query.should(init: QueryField.() -> Unit) = field(QueryFieldType.Should, init)
fun Query.must(init: QueryField.() -> Unit) = field(QueryFieldType.Must, init)
fun Query.bool(init: QueryField.() -> Unit) = field(QueryFieldType.Bool, init)

private fun QueryField.field(type: QueryFieldType, init: QueryField.() -> Unit) = initQueryField(QueryField(type), init)
fun QueryField.term(init: QueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun QueryField.prefix(init: QueryField.() -> Unit) = field(QueryFieldType.Prefix, init)
fun QueryField.filter(init: QueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun QueryField.should(init: QueryField.() -> Unit) = field(QueryFieldType.Should, init)
fun QueryField.must(init: QueryField.() -> Unit) = field(QueryFieldType.Must, init)
fun QueryField.bool(init: QueryField.() -> Unit) = field(QueryFieldType.Bool, init)

fun Query.sort(init: QuerySort.() -> Unit) {
    sort.init()
}