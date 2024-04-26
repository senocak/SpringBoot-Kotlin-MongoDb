package com.github.senocak.auth.domain.dto

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.github.senocak.auth.exception.ServerException
import com.github.senocak.auth.util.OmaErrorMessageType
import com.github.senocak.auth.util.logger
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.PageRequest
import org.slf4j.Logger
import org.springframework.data.domain.Page
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus

data class PaginationCriteria(
    var page: Int,
    var size: Int
){
    var sortBy: String? = null
    var sort: String? = null
    var columns: ArrayList<String> = arrayListOf()
}

@JsonPropertyOrder("page", "pages", "total", "sort", "sortBy", "items")
open class PaginationResponse<T, P>(
    page: Page<T>,
    items: List<P>,

    @Schema(example = "id", description = "Sort by", required = true, name = "sortBy", type = "String")
    var sortBy: String? = null,

    @Schema(example = "asc", description = "Sort", required = true, name = "sort", type = "String")
    var sort: String? = null
) : BaseDto() {
    @Schema(example = "1", description = "Current page", required = true, name = "page", type = "String")
    var page: Int = page.number + 1

    @Schema(example = "3", description = "Total pages", required = true, name = "pages", type = "String")
    var pages: Int = page.totalPages

    @Schema(example = "10", description = "Total elements", required = true, name = "total", type = "String")
    var total: Long = page.totalElements

    @ArraySchema(schema = Schema(description = "items", required = true, type = "ListDto"))
    var items: List<P>? = items

    override fun toString(): String = "PaginationResponse(page: $page, pages: $pages, total: $total, items: $items)"
}

object PageRequestBuilder {
    private val log: Logger by logger()

    fun build(paginationCriteria: PaginationCriteria): PageRequest {
        if (paginationCriteria.page < 1) {
            "Page must be greater than 0!"
                .also { log.warn(it) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    variables = arrayOf(this), statusCode = HttpStatus.BAD_REQUEST) }
        }
        paginationCriteria.page -= 1
        if (paginationCriteria.size < 1) {
            "Size must be greater than 0!"
                .also { log.warn(it) }
                .run { throw ServerException(omaErrorMessageType = OmaErrorMessageType.BASIC_INVALID_INPUT,
                    variables = arrayOf(this), statusCode = HttpStatus.BAD_REQUEST) }
        }
        val pageRequest: PageRequest = PageRequest.of(paginationCriteria.page, paginationCriteria.size)
        if (paginationCriteria.sortBy != null && paginationCriteria.sort != null) {
            val direction: Sort.Direction = when (paginationCriteria.sort) {
                "desc" -> Sort.Direction.DESC
                else -> Sort.Direction.ASC
            }
            if (paginationCriteria.columns.contains(element = paginationCriteria.sortBy))
                return pageRequest.withSort(Sort.by(direction, paginationCriteria.sortBy))
        }
        return pageRequest
    }
}