package com.beeftechlabs.repository.elastic

import com.beeftechlabs.repository.elastic.QueryField.*

data class Query(
    var index: String = "",
    val query: MutableList<QueryField<*>> = mutableListOf(),
    var sort: QuerySort = QuerySort(),
    var size: Int = 20
) {
    inline fun <reified R : QueryField<*>> initQueryField(field: R, init: R.() -> Unit) {
        field.init()
        query.add(field)
    }

    fun hasSort() = sort.name.isNotEmpty()
}

sealed class QueryField<T>(var type: QueryFieldType = QueryFieldType.Bool) {
    var name: String = ""
    var value: T = this.initValue()
    val children: MutableList<QueryField<*>> = mutableListOf()

    protected abstract fun initValue(): T

    inline fun <reified R : QueryField<*>> initQueryField(field: R, init: R.() -> Unit) {
        field.init()
        children.add(field)
    }

    class StringQueryField(type: QueryFieldType) : QueryField<String>(type) {
        override fun initValue(): String = ""
    }

    class LongQueryField(type: QueryFieldType) : QueryField<Long>(type) {
        override fun initValue(): Long = 0
    }

    class RangeQueryField(type: QueryFieldType) : QueryField<Long>(type) {
        override fun initValue(): Long = 0

        var direction: RangeDirection = RangeDirection.Gte
    }
}

enum class RangeDirection { Gt, Gte, Lt, Lte }

enum class SortOrder { Asc, Desc }

data class QuerySort(
    var name: String = "",
    var order: SortOrder = SortOrder.Asc
)

enum class QueryFieldType { Term, Prefix, Filter, Should, Must, Bool }

fun elasticQuery(init: Query.() -> Unit): Query {
    val query = Query()
    query.init()
    return query
}

private inline fun <reified T : QueryField<*>> Query.field(type: QueryFieldType, init: T.() -> Unit) =
    initQueryField(T::class.java.getConstructor(QueryFieldType::class.java).newInstance(type), init)

fun Query.term(init: StringQueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun Query.longTerm(init: LongQueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun Query.prefix(init: StringQueryField.() -> Unit) = field(QueryFieldType.Prefix, init)
fun Query.filter(init: StringQueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun Query.filterRange(init: RangeQueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun Query.should(init: StringQueryField.() -> Unit) = field(QueryFieldType.Should, init)
fun Query.must(init: StringQueryField.() -> Unit) = field(QueryFieldType.Must, init)
fun Query.bool(init: StringQueryField.() -> Unit) = field(QueryFieldType.Bool, init)

private inline fun <reified T : QueryField<*>> QueryField<*>.field(type: QueryFieldType, init: T.() -> Unit) =
    initQueryField(T::class.java.getConstructor(QueryFieldType::class.java).newInstance(type), init)

fun QueryField<*>.term(init: StringQueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun QueryField<*>.longTerm(init: LongQueryField.() -> Unit) = field(QueryFieldType.Term, init)
fun QueryField<*>.prefix(init: StringQueryField.() -> Unit) = field(QueryFieldType.Prefix, init)
fun QueryField<*>.filter(init: StringQueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun QueryField<*>.filterRange(init: RangeQueryField.() -> Unit) = field(QueryFieldType.Filter, init)
fun QueryField<*>.should(init: StringQueryField.() -> Unit) = field(QueryFieldType.Should, init)
fun QueryField<*>.must(init: StringQueryField.() -> Unit) = field(QueryFieldType.Must, init)
fun QueryField<*>.bool(init: StringQueryField.() -> Unit) = field(QueryFieldType.Bool, init)

fun Query.sort(init: QuerySort.() -> Unit) {
    sort.init()
}