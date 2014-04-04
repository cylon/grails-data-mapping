package org.grails.datastore.gorm.neo4j;

import org.grails.datastore.gorm.neo4j.engine.CypherEngine;
import org.grails.datastore.mapping.core.OptimisticLockingException;
import org.grails.datastore.mapping.core.impl.PendingUpdateAdapter;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.Simple;
import org.neo4j.helpers.collection.IteratorUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by stefan on 15.02.14.
 */
class NodePendingUpdate extends PendingUpdateAdapter<Object, Long> {

    private CypherEngine cypherEngine;
    private MappingContext mappingContext;

    public NodePendingUpdate(EntityAccess ea, CypherEngine cypherEngine, MappingContext mappingContext) {
        super(ea.getPersistentEntity(), (Long) ea.getIdentifier(), ea.getEntity(), ea);
        this.cypherEngine = cypherEngine;
        this.mappingContext = mappingContext;
    }

    @Override
    public void run() {
        Map<String, Object> simpleProps = new HashMap<String, Object>();
        Object id = getEntityAccess().getIdentifier();
        simpleProps.put("__id__", id);

        PersistentEntity persistentEntity = getEntityAccess().getPersistentEntity();
        for (PersistentProperty pp : persistentEntity.getPersistentProperties()) {
            if (pp instanceof Simple) {
                String name = pp.getName();
                Object value = getEntityAccess().getProperty(name);
                if (value != null) {
                    simpleProps.put(name,  Neo4jUtils.mapToAllowedNeo4jType( value, mappingContext));
                }
            }
        }

        String labels = ((GraphPersistentEntity)entity).getLabelsWithInheritance();

        Map<String,Object> params = new HashMap<String, Object>();
        params.put("props", simpleProps);
        params.put("id", id);

        //TODO: set n={props} might remove dynamic properties
        StringBuilder cypherStringBuilder = new StringBuilder();
        cypherStringBuilder.append("MATCH (n%s) WHERE n.__id__={id}");
        if (persistentEntity.hasProperty("version", Long.class) && persistentEntity.isVersioned()) {
            cypherStringBuilder.append(" AND n.version={version}");
            params.put("version", ((Long)getEntityAccess().getProperty("version")) - 1);
        }
        cypherStringBuilder.append(" SET n={props} RETURN id(n) as id");
        String cypher = String.format(cypherStringBuilder.toString(), labels);


        Map<String, Object> result = IteratorUtil.singleOrNull(cypherEngine.execute(cypher, params));
        if (result == null) {
            throw new OptimisticLockingException(persistentEntity, id);
        }

    }
}