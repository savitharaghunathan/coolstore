package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnit;

@Dependent
public class Resources {

    @Inject
    private EntityManager em;

    public EntityManager getEntityManager() {
        return em;
    }
}
