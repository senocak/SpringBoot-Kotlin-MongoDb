package com.github.senocak.auth.domain

import java.lang.reflect.Field
import java.time.LocalDateTime
import java.util.Arrays

// https://aravinda-kumar.com/docs/Java/Spring%20Boot/filteringAndPaginationInSpringBootMongoDB/index.html
class Filtering {
    private var filterList = ArrayList<Filter>()

    fun addFilter(key: String, operator: Operator, value: Any) {
        filterList.add(Filter(key, operator, value))
    }

    fun getFilterList(): List<Filter> = filterList

    enum class Operator(private val operator: String) {
        eq("eq"),
        gt("gt"),
        gte("gte"),
        `in`("in"),
        lt("lt"),
        lte("lte"),
        ne("ne"),
        nin("nin"),
        regex("regex");

        override fun toString(): String = this.operator

        companion object {
            fun fromString(operator: String): Operator? {
                for (op in entries) {
                    if (op.operator.equals(operator, ignoreCase = true)) {
                        return op
                    }
                }
                return null
            }
        }
    }

    inner class Filter(var key: String, var operator: Operator, var value: Any) {
        override fun toString(): String = key + operator + value
    }
}


object FilteringFactory {
    internal fun interface Converter {
        fun convert(value: String): Any?
    }

    private val converterForClass: MutableMap<Class<*>?, Converter> = HashMap()

    // add converters for all types that you want to support
    // keep it to primitive types, don't add converters for your own classes
    init {
        converterForClass[String::class.java] = Converter { value: String -> value }
        converterForClass[Int::class.java] = Converter { s: String -> s }
        converterForClass[Int::class.javaPrimitiveType] = Converter { s: String -> s }
        converterForClass[Long::class.javaPrimitiveType] = Converter { s: String -> s }
        converterForClass[Long::class.java] = Converter { s: String -> s }
        converterForClass[Double::class.java] = Converter { s: String -> s }
        converterForClass[Double::class.javaPrimitiveType] = Converter { s: String -> s }
        converterForClass[Float::class.java] = Converter { s: String -> s }
        converterForClass[Float::class.javaPrimitiveType] = Converter { s: String -> s }
        converterForClass[Boolean::class.java] = Converter { s: String -> s }
        converterForClass[Boolean::class.javaPrimitiveType] = Converter { s: String -> s }
        converterForClass[LocalDateTime::class.java] = Converter { text: String -> LocalDateTime.parse(text) }
    }

    fun <T> parseFromParams(filter: List<String>, typeParameterClass: Class<T>): Filtering {
        val filtering = Filtering()

        // a filter is in the format: key|operator|value
        for (filterString in filter) {
            // first split by | to get the key, operator and value
            val filterSplit = filterString.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            // check if the filter is in the correct format
            require(filterSplit.size == 3) { "Filtering parameter is not in the correct format" }

            try {
                // parse the operator
                val operator = Filtering.Operator.fromString(filterSplit[1])

                // the key can be nested, so we split by . to get the nested keys
                // example1: key1.key2.key3 will result in nested = [key1, key2, key3]
                // example2: key1 will result in nested = [key1]
                val nested = filterSplit[0].split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                // if the operator is "in" or "nin", we need to split the value by ; to get the list of values
                // "in" and nin are the only operators that can have multiple values
                // "in" checks if the value is in the list of values
                // "nin" checks if the value is not in the list of values
                if (operator == Filtering.Operator.`in` || operator == Filtering.Operator.nin) {
                    val list: MutableSet<Any> = HashSet()
                    for (value in filterSplit[2].split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                        list.add(element = nestedObject(classParameter = typeParameterClass, value = value, nested = nested))
                    }

                    // add the filter to the filtering object
                    filtering.addFilter(key = filterSplit[0], operator = operator, value = list)
                } else {
                    // add the filter to the filtering object

                    filtering.addFilter(
                        key = filterSplit[0],
                        operator = Filtering.Operator.fromString(filterSplit[1])!!,
                        value = nestedObject(classParameter = typeParameterClass, value = filterSplit[2], nested = nested)
                    )
                }
            } catch (e: NoSuchFieldException) {
                throw IllegalArgumentException("Filtering parameter not allowed: " + filterSplit[0])
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }
        return filtering
    }

    // this method uses recursion to get the last primitive type in the nested keys by travelling down the object tree
    // this recursion method is safe because in the worst case, the number of recursive calls is equal to the number of nested keys
    // in contradiction to the popular belief, recursion is not always bad and when you need to travel down an object tree, it is the best solution
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun nestedObject(classParameter: Class<*>, value: String, nested: Array<String>): Any {
        val field: Field = classParameter.getDeclaredField(nested[0])
        if (nested.size > 1) {
            // if there are more nested keys, we need to travel down the object tree
            // we do this by calling this method again by removing the first nested key and passing the type of the field
            // along with the value and the remaining nested keys
            return nestedObject(classParameter = field.type, value = value, nested = Arrays.copyOfRange(nested, 1, nested.size))
        }

        // when nested.length == 1, we have reached the last nested key
        // not only have reached the last nested key, but we also have the type of the field
        // using the type of the field, we can get the correct converter from the map and convert the value
        return converterForClass[field.type]!!.convert(value) ?: throw IllegalArgumentException("Filtering not supported for: ." + nested[0])
    }
}


class PaginatedResponse<T> {
    var currentPage = 0
    var totalItems: Long = 0
    var totalPages = 0
    var items: List<T>? = null
    var hasNext = false
}
class PaginatedResponseMongoTemplate<T> {
    var page = 0
    var size = 0
    var items: List<T>? = null
}