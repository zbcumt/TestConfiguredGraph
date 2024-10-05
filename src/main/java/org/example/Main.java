package org.example;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.apache.tinkerpop.gremlin.driver.remote.DriverRemoteConnection;
import org.apache.tinkerpop.gremlin.process.traversal.Bindings;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.attribute.Geoshape;
import org.janusgraph.util.system.ConfigurationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    // used for bindings
    private static final String NAME = "name";
    private static final String AGE = "age";
    private static final String TIME = "time";
    private static final String REASON = "reason";
    private static final String PLACE = "place";
    private static final String LABEL = "label";
    private static final String OUT_V = "outV";
    private static final String IN_V = "inV";

    protected Cluster cluster;
    protected Client client;
    protected Configuration conf;
    protected GraphTraversalSource g;

    protected String graphName;
    protected String propFileName;
    protected boolean supportsGeoshape;
    protected boolean supportsTransactions;

    public Main(final String graphName, final String fileName) {
        this.graphName = graphName;
        this.propFileName = fileName;
        this.supportsGeoshape = true;
        this.supportsTransactions = true;
    }

    protected GraphTraversalSource createGraph() {
        LOGGER.info("creating graph");

        final StringBuilder s = new StringBuilder();

        s.append("ConfiguredGraphFactory.getGraphNames();");

        final ResultSet resultSet = client.submit(s.toString());
        Stream<Result> futureList = resultSet.stream();
        // 遍历 Stream，判断是否与graphName相等
        AtomicBoolean isExist = new AtomicBoolean(false);
        futureList.map(Result::getString).forEach(resultString -> {
            if (this.graphName.equals(resultString)) {
                LOGGER.info("Found match: " + resultString);
                isExist.set(true);
            } else {
                LOGGER.info("No match: " + resultString);
            }
        });

        // 创建图
        if (!isExist.get()) {
            s.setLength(0); // 清空
            s.append("HashMap map = new HashMap<String, Object>();");
            s.append("map.put(\"storage.backend\", \"cql\");");
            s.append("map.put(\"storage.hostname\", \"127.0.0.1\");");
            s.append("map.put(\"graph.graphname\", \"" + this.graphName + "\");");
            s.append("ConfiguredGraphFactory.createConfiguration(new MapConfiguration(map));");

            final ResultSet resultSet2 = client.submit(s.toString());
            // drain the results completely
            Stream<Result> futureList2 = resultSet2.stream();
            futureList2.map(Result::toString).forEach(LOGGER::info);
        }

        return g = traversal().withRemote(DriverRemoteConnection.using(cluster, this.graphName + "_traversal"));
    }

    protected void createScheme() {
        LOGGER.info("creating scheme");

        final StringBuilder s = new StringBuilder();

        s.append("JanusGraphManagement management = ConfiguredGraphFactory.open(\"" + this.graphName + "\").openManagement();");

        // properties
        s.append("PropertyKey name = management.makePropertyKey(\"name\").dataType(String.class).make(); ");
        s.append("PropertyKey age = management.makePropertyKey(\"age\").dataType(Integer.class).make(); ");
        s.append("PropertyKey time = management.makePropertyKey(\"time\").dataType(Integer.class).make(); ");
        s.append("PropertyKey reason = management.makePropertyKey(\"reason\").dataType(String.class).make(); ");
        s.append("PropertyKey place = management.makePropertyKey(\"place\").dataType(Geoshape.class).make(); ");

        // vertex labels
        s.append("management.makeVertexLabel(\"titan\").make(); ");
        s.append("management.makeVertexLabel(\"location\").make(); ");
        s.append("management.makeVertexLabel(\"god\").make(); ");
        s.append("management.makeVertexLabel(\"demigod\").make(); ");
        s.append("management.makeVertexLabel(\"human\").make(); ");
        s.append("management.makeVertexLabel(\"monster\").make(); ");

        // edge labels
        s.append("management.makeEdgeLabel(\"father\").multiplicity(Multiplicity.MANY2ONE).make(); ");
        s.append("management.makeEdgeLabel(\"mother\").multiplicity(Multiplicity.MANY2ONE).make(); ");
        s.append("management.makeEdgeLabel(\"lives\").signature(reason).make(); ");
        s.append("management.makeEdgeLabel(\"pet\").make(); ");
        s.append("management.makeEdgeLabel(\"brother\").make(); ");
        s.append("management.makeEdgeLabel(\"battled\").make(); ");

        // composite indexes
        s.append("management.buildIndex(\"nameIndex\", Vertex.class).addKey(name).buildCompositeIndex(); ");

        s.append("management.commit();");

        final ResultSet resultSet = client.submit(s.toString());
        // drain the results completely
        Stream<Result> futureList = resultSet.stream();
        futureList.map(Result::toString).forEach(LOGGER::info);
    }

    public void ConnectJanusServer() throws ConfigurationException {
        LOGGER.info("opening graph");
        conf = ConfigurationUtil.loadPropertiesConfig(propFileName);

        // using the remote driver for schema
        try {
            cluster = Cluster.open(conf.getString("gremlin.remote.driver.clusterFile"));
            client = cluster.connect();
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
    }

    public void createElements() {
        LOGGER.info("creating elements");

        // Use bindings to allow the Gremlin Server to cache traversals that
        // will be reused with different parameters. This minimizes the
        // number of scripts that need to be compiled and cached on the server.
        // https://tinkerpop.apache.org/docs/3.2.6/reference/#parameterized-scripts
        final Bindings b = Bindings.instance();

        // see GraphOfTheGodsFactory.java

        Vertex saturn = g.addV(b.of(LABEL, "titan")).property(NAME, b.of(NAME, "saturn"))
                .property(AGE, b.of(AGE, 10000)).next();
        Vertex sky = g.addV(b.of(LABEL, "location")).property(NAME, b.of(NAME, "sky")).next();
        Vertex sea = g.addV(b.of(LABEL, "location")).property(NAME, b.of(NAME, "sea")).next();
        Vertex jupiter = g.addV(b.of(LABEL, "god")).property(NAME, b.of(NAME, "jupiter")).property(AGE, b.of(AGE, 5000))
                .next();
        Vertex neptune = g.addV(b.of(LABEL, "god")).property(NAME, b.of(NAME, "neptune")).property(AGE, b.of(AGE, 4500))
                .next();
        Vertex hercules = g.addV(b.of(LABEL, "demigod")).property(NAME, b.of(NAME, "hercules"))
                .property(AGE, b.of(AGE, 30)).next();
        Vertex alcmene = g.addV(b.of(LABEL, "human")).property(NAME, b.of(NAME, "alcmene")).property(AGE, b.of(AGE, 45))
                .next();
        Vertex pluto = g.addV(b.of(LABEL, "god")).property(NAME, b.of(NAME, "pluto")).property(AGE, b.of(AGE, 4000))
                .next();
        Vertex nemean = g.addV(b.of(LABEL, "monster")).property(NAME, b.of(NAME, "nemean")).next();
        Vertex hydra = g.addV(b.of(LABEL, "monster")).property(NAME, b.of(NAME, "hydra")).next();
        Vertex cerberus = g.addV(b.of(LABEL, "monster")).property(NAME, b.of(NAME, "cerberus")).next();
        Vertex tartarus = g.addV(b.of(LABEL, "location")).property(NAME, b.of(NAME, "tartarus")).next();

        g.V(b.of(OUT_V, jupiter)).as("a").V(b.of(IN_V, saturn)).addE(b.of(LABEL, "father")).from("a").next();
        g.V(b.of(OUT_V, jupiter)).as("a").V(b.of(IN_V, sky)).addE(b.of(LABEL, "lives"))
                .property(REASON, b.of(REASON, "loves fresh breezes")).from("a").next();
        g.V(b.of(OUT_V, jupiter)).as("a").V(b.of(IN_V, neptune)).addE(b.of(LABEL, "brother")).from("a").next();
        g.V(b.of(OUT_V, jupiter)).as("a").V(b.of(IN_V, pluto)).addE(b.of(LABEL, "brother")).from("a").next();

        g.V(b.of(OUT_V, neptune)).as("a").V(b.of(IN_V, sea)).addE(b.of(LABEL, "lives"))
                .property(REASON, b.of(REASON, "loves waves")).from("a").next();
        g.V(b.of(OUT_V, neptune)).as("a").V(b.of(IN_V, jupiter)).addE(b.of(LABEL, "brother")).from("a").next();
        g.V(b.of(OUT_V, neptune)).as("a").V(b.of(IN_V, pluto)).addE(b.of(LABEL, "brother")).from("a").next();

        g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, jupiter)).addE(b.of(LABEL, "father")).from("a").next();
        g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, alcmene)).addE(b.of(LABEL, "mother")).from("a").next();

        if (supportsGeoshape) {
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, nemean)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 1)).property(PLACE, b.of(PLACE, Geoshape.point(38.1f, 23.7f))).from("a")
                    .next();
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, hydra)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 2)).property(PLACE, b.of(PLACE, Geoshape.point(37.7f, 23.9f))).from("a")
                    .next();
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, cerberus)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 12)).property(PLACE, b.of(PLACE, Geoshape.point(39f, 22f))).from("a")
                    .next();
        } else {
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, nemean)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 1)).property(PLACE, b.of(PLACE, getGeoFloatArray(38.1f, 23.7f)))
                    .from("a").next();
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, hydra)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 2)).property(PLACE, b.of(PLACE, getGeoFloatArray(37.7f, 23.9f)))
                    .from("a").next();
            g.V(b.of(OUT_V, hercules)).as("a").V(b.of(IN_V, cerberus)).addE(b.of(LABEL, "battled"))
                    .property(TIME, b.of(TIME, 12)).property(PLACE, b.of(PLACE, getGeoFloatArray(39f, 22f))).from("a")
                    .next();
        }

        g.V(b.of(OUT_V, pluto)).as("a").V(b.of(IN_V, jupiter)).addE(b.of(LABEL, "brother")).from("a").next();
        g.V(b.of(OUT_V, pluto)).as("a").V(b.of(IN_V, neptune)).addE(b.of(LABEL, "brother")).from("a").next();
        g.V(b.of(OUT_V, pluto)).as("a").V(b.of(IN_V, tartarus)).addE(b.of(LABEL, "lives"))
                .property(REASON, b.of(REASON, "no fear of death")).from("a").next();
        g.V(b.of(OUT_V, pluto)).as("a").V(b.of(IN_V, cerberus)).addE(b.of(LABEL, "pet")).from("a").next();

        g.V(b.of(OUT_V, cerberus)).as("a").V(b.of(IN_V, tartarus)).addE(b.of(LABEL, "lives")).from("a").next();
    }

    /**
     * Returns the geographical coordinates as a float array.
     */
    protected float[] getGeoFloatArray(final float lat, final float lon) {
        return new float[]{ lat, lon };
    }

    /**
     * Runs some traversal queries to get data from the graph.
     */
    public void readElements() {
        try {
            if (g == null) {
                return;
            }

            LOGGER.info("reading elements");

            // look up vertex by name can use a composite index in JanusGraph
            final Optional<Map<Object, Object>> v = g.V().has("name", "jupiter").valueMap(true).tryNext();
            if (v.isPresent()) {
                LOGGER.info(v.get().toString());
            } else {
                LOGGER.warn("jupiter not found");
            }

            // look up an incident edge
            final Optional<Map<Object, Object>> edge = g.V().has("name", "hercules").outE("battled").as("e").inV()
                    .has("name", "hydra").select("e").valueMap(true).tryNext();
            if (edge.isPresent()) {
                LOGGER.info(edge.get().toString());
            } else {
                LOGGER.warn("hercules battled hydra not found");
            }

            // numerical range query can use a mixed index in JanusGraph
            final List<Object> list = g.V().has("age", P.gte(5000)).values("age").toList();
            LOGGER.info(list.toString());

            // pluto might be deleted
            final boolean plutoExists = g.V().has("name", "pluto").hasNext();
            if (plutoExists) {
                LOGGER.info("pluto exists");
            } else {
                LOGGER.warn("pluto not found");
            }

            // look up jupiter's brothers
            final List<Object> brothers = g.V().has("name", "jupiter").both("brother").values("name").dedup().toList();
            LOGGER.info("jupiter's brothers: " + brothers.toString());

        } finally {
            // the default behavior automatically starts a transaction for
            // any graph interaction, so it is best to finish the transaction
            // even for read-only graph query operations
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Makes an update to the existing graph structure. Does not create any
     * new vertices or edges.
     */
    public void updateElements() {
        try {
            if (g == null) {
                return;
            }
            LOGGER.info("updating elements");
            final long ts = System.currentTimeMillis();
            g.V().has("name", "jupiter").property("ts", ts).iterate();
            if (supportsTransactions) {
                g.tx().commit();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    /**
     * Deletes elements from the graph structure. When a vertex is deleted,
     * its incident edges are also deleted.
     */
    public void deleteElements() {
        try {
            if (g == null) {
                return;
            }
            LOGGER.info("deleting elements");
            // note that this will succeed whether or not pluto exists
            g.V().has("name", "pluto").drop().iterate();
            if (supportsTransactions) {
                g.tx().commit();
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            if (supportsTransactions) {
                g.tx().rollback();
            }
        }
    }

    public void closeGraph() throws Exception {
        LOGGER.info("closing graph");
        try {
            if (g != null) {
                // this closes the remote, no need to close the empty graph
                g.close();
            }
            if (cluster != null) {
                // the cluster closes all of its clients
                cluster.close();
            }
        } finally {
            g = null;
            client = null;
            cluster = null;
        }
    }

    public void runApp() {
        try {
            // open and initialize the graph
            ConnectJanusServer();

            // create dynamic graph and define the schema before loading data
            createGraph();

            // define the schema before loading data
            createScheme();

            // build the graph structure
            createElements();
            // read to see they were made
            readElements();

            for (int i = 0; i < 3; i++) {
                try {
                    Thread.sleep((long) (Math.random() * 500) + 500);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getMessage(), e);
                }
                // update some graph elements with changes
                updateElements();
                // read to see the changes were made
                readElements();
            }

            // delete some graph elements
            deleteElements();
            // read to see the changes were made
            readElements();

            // close the graph
            closeGraph();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        final String graphName = (args != null && args.length > 0) ? args[0] : "graph1";
        final String fileName = (args != null && args.length > 1) ? args[1] : "null";
        final Main app = new Main(graphName, fileName);
        app.runApp();
    }
}
