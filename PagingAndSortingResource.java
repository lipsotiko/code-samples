package com.dol.ecomp.resources.universal;

import com.dol.ecomp.jpa.dao.JpaDao;
import com.dol.ecomp.jpa.dao.pagination.Page;
import com.dol.ecomp.jpa.dao.pagination.SortablePageRequest;
import com.dol.ecomp.jpa.entities.Identifiable;
import com.google.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

public class PagingAndSortingResource<T extends Identifiable, DAO extends JpaDao<T, Integer>> {

    @Inject
    private DAO dao;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Page<T> get(
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("sortBy") String sortBy) {
        return dao.readPage(new SortablePageRequest(page, pageSize, sortBy));
    }
}
