package com.redhat.coolstore.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class Resources {

    @Inject
    EntityManager em;

    @Produces
    public EntityManager getEntityManager() {
        return em;
    }
}
