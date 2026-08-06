package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@Dependent
public class Resources {

    @Inject
    EntityManager em;

    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
}
