package com.dol.ecomp.jpa.dao;

import com.dol.ecomp.jpa.dao.pagination.Page;
import com.dol.ecomp.jpa.dao.pagination.Pageable;
import com.dol.ecomp.jpa.dao.pagination.Sortable;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.Column;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import org.eclipse.persistence.internal.jpa.EJBQueryImpl;
import org.eclipse.persistence.jpa.JpaEntityManager;
import org.eclipse.persistence.queries.DatabaseQuery;
import org.eclipse.persistence.sessions.DatabaseRecord;
import org.eclipse.persistence.sessions.Session;

public class JpaReadOnlyDao<T, KeyT extends Serializable> implements ReadOnlyDao<T, KeyT> {

    protected Class<T> entityClass;

    @Inject
    protected Provider<EntityManager> emp;

    public JpaReadOnlyDao() {
        ParameterizedType genericSuperclass = (ParameterizedType) getClass()
                .getGenericSuperclass();
        this.entityClass = (Class<T>) genericSuperclass.getActualTypeArguments()[0];
    }

    @Override
    public T read(KeyT id) {
        return this.emp.get().find(entityClass, id);
    }

    @Override
    public Collection<T> read() {
        Query query = emp.get().createQuery(
                "SELECT entity FROM " + this.entityClass.getSimpleName() + " entity");
        try {
            return (Collection<T>) query.getResultList();
        } catch (NoResultException nre) {
            return null;
        }
    }

    @Override
    public Page<T> readPage(SortablePageable sortablePageable) {
        CriteriaBuilder cb = emp.get().getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(this.entityClass);
        Root<T> root = query.from(this.entityClass);
        query.select(root);
        appendOrderBy(query, sortablePageable);
        TypedQuery<T> typedQuery = emp.get().createQuery(query);
        return buildPage(typedQuery, sortablePageable, count());
    }

    @Override
    public Page<T> readPage(CriteriaQuery<T> query, SortablePageable pageRequest) {
        // Count must be performed prior to appending ORDER BY clause
        Long count = count(emp.get().createQuery(query));
        appendOrderBy(query, pageRequest);
        TypedQuery<T> sortedQuery = emp.get().createQuery(query);
        return buildPage(sortedQuery, pageRequest, count);
    }

    @Override
    public Page<T> readPage(TypedQuery<T> query, Pageable pageable) {
        return buildPage(query, pageable, count(query));
    }

    private void appendOrderBy(CriteriaQuery<T> q, Sortable sortable) {
        if (sortable.getSort().isEmpty()) {
            return;
        }
        CriteriaBuilder cb = emp.get().getCriteriaBuilder();
        q.getRoots().stream().findFirst().ifPresent(root -> {
            List<Order> orders = sortable.getSort().stream().map(sort -> {
                Path<String> path = buildNestedFieldPath(root, sort.getSortBy());
                if (sort.getSortDirection().equals(SortDirection.ASC)) {
                    return cb.asc(path);
                } else {
                    return cb.desc(path);
                }
            }).collect(Collectors.toList());
            q.orderBy(orders);
        });
    }

    private Path<String> buildNestedFieldPath(Root<?> root, String field) {
        if (field.contains(".")) {
            String[] fields = field.split("\\.");
            Path<String> path = null;
            for (String f : fields) {
                path = (path == null) ? root.get(f) : path.get(f);
            }
            return path;
        } else {
            return root.get(field);
        }
    }

    @Override
    public Long count() {
        TypedQuery<Long> query = emp.get().createQuery(
                "SELECT count(entity) FROM " + this.entityClass.getSimpleName()
                        + " entity", Long.class);
        return query.getSingleResult();
    }

    private Long count(TypedQuery<T> query) {
        Session session = emp.get().unwrap(JpaEntityManager.class).getActiveSession();
        EJBQueryImpl<T> queryImpl = (EJBQueryImpl<T>) query;
        DatabaseQuery databaseQuery = queryImpl.getDatabaseQuery();
        DatabaseRecord databaseRecord = new DatabaseRecord();
        query.getParameters().forEach(parameter -> {
            String name = parameter.getName();
            Object value = queryImpl.getParameterValue(name);
            databaseRecord.put(name, value);
        });

        String sqlString = databaseQuery.getTranslatedSQLString(session, databaseRecord);
        Integer singleResult = (Integer) emp.get()
                .createNativeQuery("select count(distinct " + idField() + ") from (" + sqlString + ") q").getSingleResult();
        return Long.valueOf(singleResult);
    }

    private String idField() {
        // Look for the @Column name of the @Id field
        // If the @Column annotation is not found, assume the identity field is named 'id'
        String idField = null;
        for (Field field : this.entityClass.getDeclaredFields()) {
            Annotation[] annotations = field.getDeclaredAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(Id.class)) {
                    Column column = field.getAnnotation(Column.class);
                    if (column != null) {
                        idField = column.name();
                    }
                }
            }
        }

        return idField == null ? "id" : idField;
    }

    private Page<T> buildPage(TypedQuery<T> query, Pageable pageable, Long count) {
        Integer pageSize = pageable.getPageSize();
        Integer pageNumber = pageable.getPage();
        List<T> resultList = query
                .setFirstResult((pageNumber * pageSize) - pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return Page.of(resultList, pageNumber, pageSize, count);
    }

    public void refresh(T t) {
        this.emp.get().refresh(t);
    }
}

