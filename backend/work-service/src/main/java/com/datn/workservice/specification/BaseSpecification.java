package com.datn.workservice.specification;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

public abstract class BaseSpecification {

    public static <T> Specification<T> hasFieldContaining(String field, String value) {
        return (root, query, cb) -> (value == null || value.isBlank())
                ? cb.conjunction()
                : cb.like(cb.lower(root.get(field)), "%" + value.trim().toLowerCase() + "%");
    }

    public static <T> Specification<T> hasFieldEqual(String field, Object value) {
        return (root, query, cb) -> (value == null)
                ? cb.conjunction()
                : cb.equal(root.get(field), value);
    }

    public static <T> Specification<T> hasNestedFieldEqual(String parentField, String childField, Object value) {
        return (root, query, cb) -> (value == null)
                ? cb.conjunction()
                : cb.equal(root.get(parentField).get(childField), value);
    }

    public static <T> Specification<T> searchByKeyword(String keyword, String... fields) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank() || fields.length == 0) return cb.conjunction();
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            Predicate[] predicates = Arrays.stream(fields)
                    .map(field -> cb.like(cb.lower(root.get(field)), pattern))
                    .toArray(Predicate[]::new);
            return cb.or(predicates);
        };
    }

    public static <T> Specification<T> hasIdIn(String field, List<?> ids) {
        return (root, query, cb) -> (ids == null || ids.isEmpty())
                ? cb.conjunction()
                : root.get(field).in(ids);
    }

    public static <T, Y extends Comparable<? super Y>> Specification<T> isBetween(String field, Y from, Y to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return cb.conjunction();
            if (from != null && to != null) return cb.between(root.get(field), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get(field), from);
            return cb.lessThanOrEqualTo(root.get(field), to);
        };
    }
}
