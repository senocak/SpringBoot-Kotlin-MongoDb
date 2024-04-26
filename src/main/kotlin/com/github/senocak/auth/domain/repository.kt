package com.github.senocak.auth.domain

import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository: MongoRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun findByEmailActivationToken(token: String): User?
    fun existsByEmail(email: String): Boolean
}

interface JwtTokenRepository : CrudRepository<JwtToken, UUID> {
    fun findByEmail(email: String): JwtToken?
    fun findAllByEmail(email: String): List<JwtToken?>
    fun findByToken(token: String): JwtToken?
    fun findByTokenAndTokenType(token: String, type: String): JwtToken?
}

interface PasswordResetTokenRepository : CrudRepository<PasswordResetToken, UUID> {
    fun findByToken(token: String): PasswordResetToken?
    fun findByUserId(userId: UUID): PasswordResetToken?
}

// https://aravinda-kumar.com/docs/Java/Spring%20Boot/filteringAndPaginationInSpringBootMongoDB/index.html
interface FilterableRepository<T> {
    fun findAllWithFilter(typeParameterClass: Class<T>, filtering: Filtering, pageable: Pageable): Page<T>
    fun getAllPossibleValuesForFilter(typeParameterClass: Class<T>, filtering: Filtering, filterKey: String): List<Any>

    fun constructQueryFromFiltering(filtering: Filtering): Query {
        val query = Query()
        val criteriaMap: MutableMap<String, Criteria> = HashMap()
        for (filter: Filtering.Filter in filtering.getFilterList()) {
            when (filter.operator) {
                Filtering.Operator.eq -> criteriaMap[filter.key] =
                    Criteria.where(filter.key).`is`(filter.value)
                Filtering.Operator.gt -> when {
                    criteriaMap.containsKey(filter.key) -> criteriaMap.get(filter.key)?.gt(filter.value)
                    else -> criteriaMap[filter.key] = Criteria.where(filter.key).gt(filter.value)
                }
                Filtering.Operator.gte -> when {
                    criteriaMap.containsKey(filter.key) -> criteriaMap.get(filter.key)?.gte(filter.value)
                    else -> criteriaMap[filter.key] = Criteria.where(filter.key).gte(filter.value)
                }
                Filtering.Operator.`in` -> criteriaMap[filter.key] =
                    Criteria.where(filter.key).`in`(filter.value as HashSet<Any?>)
                Filtering.Operator.lt -> when {
                    criteriaMap.containsKey(filter.key) -> criteriaMap.get(filter.key)?.lt(filter.value)
                    else -> criteriaMap[filter.key] = Criteria.where(filter.key).lt(filter.value)
                }
                Filtering.Operator.lte -> when {
                    criteriaMap.containsKey(filter.key) -> criteriaMap.get(filter.key)?.lte(filter.value)
                    else -> criteriaMap[filter.key] = Criteria.where(filter.key).lte(filter.value)
                }
                Filtering.Operator.ne -> criteriaMap[filter.key] =
                    Criteria.where(filter.key).ne(filter.value)
                Filtering.Operator.nin -> criteriaMap[filter.key] =
                    Criteria.where(filter.key).nin(filter.value as HashSet<Any?>)
                Filtering.Operator.regex -> criteriaMap[filter.key] =
                    Criteria.where(filter.key).regex(("${filter.value}"))
                else -> throw IllegalArgumentException("Unknown operator: " + filter.operator)
            }
        }
        criteriaMap.values.forEach(action = query::addCriteria)
        return query
    }
}