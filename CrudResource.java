package com.dol.ecomp.resources.universal;

import com.dol.ecomp.auth.UserPrincipal;
import com.dol.ecomp.exceptions.EcompException;
import com.dol.ecomp.jpa.dao.JpaDao;
import com.dol.ecomp.jpa.entities.Identifiable;
import com.google.inject.Inject;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import javax.persistence.PersistenceException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;

/** Limited to entities with an Integer as their primary key */
@Slf4j
public abstract class CrudResource<T extends Identifiable, DAO extends JpaDao<T, Integer>>
    extends PagingAndSortingResource<T, DAO> implements CrudListener<T> {

  @Inject private DAO dao;

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public T get(@PathParam("id") Integer id, @Parameter(in = ParameterIn.HEADER, name = "Authorization") @Auth UserPrincipal userPrincipal) {
    return afterGetHandler(dao.read(id), userPrincipal);
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public T save(T t, @Parameter(in = ParameterIn.HEADER, name = "Authorization") @Auth UserPrincipal userPrincipal) {
      try {
          if (t.getId() == null) {
              T preCreatedEntity = beforeCreateHandler(t, userPrincipal);
              T createdEntity = dao.create(preCreatedEntity);
              return afterCreateHandler(createdEntity, userPrincipal);
          } else {
              T preUpdatedEntity = beforeUpdateHandler(t, userPrincipal);
              T updatedEntity = dao.update(preUpdatedEntity);
              return afterUpdateHandler(updatedEntity, userPrincipal);
          }
      } catch (PersistenceException ex) {
          log.error(ex.getMessage());
          throw new EcompException(ex.getMessage());
      }
  }

  @DELETE
  @Path("/{id}")
  public void delete(@PathParam("id") Integer id, @Parameter(in = ParameterIn.HEADER, name = "Authorization") @Auth UserPrincipal userPrincipal) {
    T t = dao.read(id);
    beforeDeleteHandler(t, userPrincipal);
    dao.delete(t);
    afterDeleteHandler(t, userPrincipal);
  }
}

