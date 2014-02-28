// @java.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.cache.query;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.affinity.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.spi.indexing.h2.*;
import org.jetbrains.annotations.*;

/**
 * Main API for configuring and executing cache queries.
 * <p>
 * The queries are executed as follows:
 * <ol>
 * <li>Query is sent to requested grid nodes.</li>
 * <li>
 *  If query is not full scan query, each node will execute the received query
 *  against index tables to retrieve the keys of the values in the result set.
 * </li>
 * <li>
 *  If the requesting node will receive a page of next query results, it will provide it to the ongoing
 *  iterator, which will start returning received key-value pairs to user. If {@link #keepAll(boolean)}
 *  flag is set to false, then the received page will be immediately discarded after it has been
 *  returned to user. In this case, the {@link GridCacheQueryFuture#get()} method will always return
 *  only the last page. If {@link #keepAll(boolean)} flag is {@code true}, then all query result pages
 *  will be accumulated in memory and the full query result will be returned to user.
 * </li>
 * </ol>
 * Cache queries are created from {@link GridCacheQueries} API via any of the available
 * {@code createXXXQuery(...)} methods.
 * <h1 class="header">SQL Queries</h1>
 * {@code SQL} query allows to execute distributed cache
 * queries using standard SQL syntax. All values participating in where clauses
 * or joins must be annotated with {@link GridCacheQuerySqlField} annotation.
 * There are almost no restrictions as to which SQL syntax can be used. All inner, outer, or
 * full joins are supported, as well as rich set of SQL grammar and functions. GridGain relies on
 * {@code H2 SQL Engine} for SQL compilation and indexing. For full set of supported
 * {@code Numeric}, {@code String}, and {@code Date/Time} SQL functions please refer
 * to <a href="http://www.h2database.com/html/functions.html">H2 Functions</a> documentation
 * directly. For full set of supported SQL syntax refer to
 * <a href="http://www.h2database.com/html/grammar.html#select">H2 SQL Select Grammar</a>.
 * <p>
 * Note that whenever using {@code 'group by'} queries, only individual page results will be
 * sorted and not the full result sets. However, if a single node is queried, then the result
 * set will be quite accurate.
 * <h2 class="header">Cross-Cache Queries</h2>
 * You are allowed to query data from several caches. Cache that this query was created on is
 * treated as default schema in this case. Other caches can be referenced by their names.
 * <p>
 * Note that cache name is case sensitive and has to always be specified in double quotes.
 * Here is an example of cross cache query (note that 'replicated' and 'partitioned' are
 * cache names for replicated and partitioned caches accordingly):
 * <pre name="code" class="java">
 * GridCacheQuery&lt;Map.Entry&lt;Integer, FactPurchase&gt;&gt; storePurchases = cache.queries().createSqlQuery(
 *     Purchase.class,
 *     "from \"replicated\".Store, \"partitioned\".Purchase where Store.id=Purchase.storeId and Store.id=?");
 * </pre>
 * <h2 class="header">Snowflake Schema and Distributed Joins</h2>
 * <a href="http://en.wikipedia.org/wiki/Snowflake_schema">Snowflake Schema</a> is a logical
 * arrangement of data in which data is split into {@code dimensions} and {@code facts}.
 * <i>Dimensions</i> can be referenced or joined by other <i>dimensions</i> or <i>facts</i>,
 * however, <i>facts</i> are generally not referenced by other facts. You can view <i>dimensions</i>
 * as your master or reference data, while <i>facts</i> are usually large data sets of events or
 * other objects that continuously come into the system and may change frequently. In GridGain
 * such architecture is supported via cross-cache queries. By storing <i>dimensions</i> in
 * {@link GridCacheMode#REPLICATED REPLICATED} caches and <i>facts</i> in much larger
 * {@link GridCacheMode#PARTITIONED PARTITIONED} caches you can freely execute distributed joins across
 * your whole in-memory data grid, thus querying your in memory data without any limitations.
 * <p>
 * Note that in addition to joining <i>facts</i> and <i>dimensions</i>,
 * if you properly colocate relevant <i>facts</i> together, you can also cross-reference and
 * execute distributed joins across multiple <i>fact</i> object types within your in-memory data grid as well.
 * <h2 class="header">Field Queries</h2>
 * By default {@code select} clause is ignored as query result contains full objects.
 * If it is needed to select individual fields, use {@link GridCacheQueries#createSqlFieldsQuery(String)} method.
 * <h2 class="header">Custom functions in SQL queries.</h2>
 * It is possible to write custom Java methods and call then form SQL queries. These methods must be public static
 * and annotated with {@link GridCacheQuerySqlFunction}. Classes containing these methods must be registered in
 * {@link GridH2IndexingSpi#setIndexCustomFunctionClasses(Class[])}.
 * <h1 class="header">Text Queries</h1>
 * GridGain supports full text queries using Apache Lucene.
 * All fields that are expected to show up in text query results must be annotated
 * with {@link GridCacheQueryTextField}.
 * <h1 class="header">Scan Queries</h1>
 * Sometimes when it is known in advance that SQL query will cause a full data scan,
 * or whenever data set is relatively small, the full scan
 * query may be used. With this query type GridGain will iterate over all cache
 * entries, skipping over the entries that don't pass the optionally provided key-value filter
 * (see {@link GridCacheQueries#createScanQuery(GridBiPredicate)} method).
 * <h1 class="header">Query Performance</h1>
 * Note that in case of very frequent cache updates, query index has to be frequently updated which
 * may have negative effect on performance. Generally, it's best to use cache indexes and queries
 * when updates are not too frequent.
 * <h2 class="header">Limitations</h2>
 * Data in GridGain cache is usually distributed across several nodes,
 * so some queries may not work as expected. Keep in mind following limitations
 * (not applied if data is queried from one node only):
 * <ul>
 *     <li>
 *         {@code Group by} and {@code sort by} statements are applied separately
 *         on each node, so result set will likely be incorrectly grouped or sorted
 *         after results from multiple remote nodes are grouped together.
 *     </li>
 *     <li>
 *         Aggregation functions like {@code sum}, {@code max}, {@code avg}, etc.
 *         are also applied on each node. Therefore you will get several results
 *         containing aggregated values, one for each node.
 *     </li>
 *     <li>
 *         Joins will work correctly only if joined objects are stored in
 *         collocated mode or at least one side of the join is stored in
 *         {@link GridCacheMode#REPLICATED} cache. Refer to
 *         {@link GridCacheAffinityKey} javadoc for more information about colocation.
 *     </li>
 * </ul>
 * <h1 class="header">Query usage</h1>
 * As an example, suppose we have data model consisting of {@code 'Employee'} and {@code 'Organization'}
 * classes defined as follows:
 * <pre name="code" class="java">
 * public class Organization {
 *     &#64;GridCacheQuerySqlField(unique = true)
 *     private long id;
 *
 *     &#64;GridCacheQuerySqlField
 *     private String name;
 *     ...
 * }
 *
 * public class Person {
 *     // Unique index.
 *     &#64;GridCacheQuerySqlField(unique=true)
 *     private long id;
 *
 *     &#64;GridCacheQuerySqlField
 *     private long orgId; // Organization ID.
 *
 *     // Not indexed.
 *     private String name;
 *
 *     // Non-unique index.
 *     &#64;GridCacheQuerySqlField
 *     private double salary;
 *
 *     // Index for text search.
 *     &#64;GridCacheQueryTextField
 *     private String resume;
 *     ...
 * }
 * </pre>
 * Then you can create and execute queries that check various salary ranges like so:
 * <pre name="code" class="java">
 * GridCache&lt;Long, Person&gt; cache = G.grid().cache();
 * ...
 * // Create query which selects salaries based on range for all employees
 * // that work for a certain company.
 * GridCacheQuery&lt;Map.Entry&lt;Long, Person&gt;&gt; qry = cache.queries().createSqlQuery(Person.class,
 *     "from Person, Organization where Person.orgId = Organization.id " +
 *         "and Organization.name = ? and Person.salary &gt; ? and Person.salary &lt;= ?");
 *
 * // Query all nodes to find all cached GridGain employees
 * // with salaries less than 1000.
 * qry.execute("GridGain", 0, 1000);
 *
 * // Query only remote nodes to find all remotely cached GridGain employees
 * // with salaries greater than 1000 and less than 2000.
 * qry.projection(grid.remoteProjection()).execute("GridGain", 1000, 2000);
 * </pre>
 * Here is a possible query that will use Lucene text search to scan all resumes to
 * check if employees have {@code Master} degree:
 * <pre name="code" class="java">
 * GridCacheQuery&lt;Map.Entry&lt;Long, Person&gt;&gt; mastersQry =
 *     cache.queries().createFullTextQuery(Person.class, "Master");
 *
 * // Query all cache nodes.
 * mastersQry.execute();
 * </pre>
 *
 * @author @java.author
 * @version @java.version
 */
public interface GridCacheQuery<T> {
    /** Default query page size. */
    public static final int DFLT_PAGE_SIZE = 1024;

    /**
     * Sets result page size. If not provided, {@link #DFLT_PAGE_SIZE} will be used.
     * Results are returned from queried nodes one page at a tme.
     *
     * @param pageSize Page size.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> pageSize(int pageSize);

    /**
     * Sets query timeout. {@code 0} means there is no timeout (this
     * is a default value).
     *
     * @param timeout Query timeout.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> timeout(long timeout);

    /**
     * Sets whether or not to keep all query results local. If not - only the current page
     * is kept locally. Default value is {@code true}.
     *
     * @param keepAll Keep results or not.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> keepAll(boolean keepAll);

    /**
     * Sets whether or not to include backup entries into query result. This flag
     * is {@code false} by default.
     *
     * @param incBackups Query {@code includeBackups} flag.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> includeBackups(boolean incBackups);

    /**
     * Sets whether or not to deduplicate query result set. If this flag is {@code true}
     * then query result will not contain some key more than once even if several nodes
     * returned entries with the same keys. Default value is {@code false}.
     *
     * @param dedup Query {@code enableDedup} flag.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> enableDedup(boolean dedup);

    /**
     * Sets optional grid projection to execute this query on.
     *
     * @param prj Projection.
     * @return {@code this} query instance for chaining.
     */
    public GridCacheQuery<T> projection(GridProjection prj);

    /**
     * Executes the query and returns the query future. Caller may decide to iterate
     * over the returned future directly in which case the iterator may block until
     * the next value will become available, or wait for the whole query to finish
     * by calling any of the {@code 'get(..)'} methods on the returned future. If
     * {@link #keepAll(boolean)} flag is set to {@code false}, then {@code 'get(..)'}
     * methods will only return the last page received, otherwise all pages will be
     * accumulated and returned to user as a collection.
     * <p>
     * Note that if the passed in grid projection is a local node, then query
     * will be executed locally without distribution to other nodes.
     * <p>
     * Also note that query state cannot be changed (clause, timeout etc.), except
     * arguments, if this method was called at least once.
     *
     * @param args Optional arguments.
     * @return Future for the query result.
     */
    public GridCacheQueryFuture<T> execute(@Nullable Object... args);

    /**
     * Executes the query the same way as {@link #execute(Object...)} method but reduces result remotely.
     *
     * @param rmtReducer Remote reducer.
     * @param args Optional arguments.
     * @return Future for the query result.
     */
    public <R> GridCacheQueryFuture<R> execute(GridReducer<T, R> rmtReducer, @Nullable Object... args);

    /**
     * Executes the query the same way as {@link #execute(Object...)} method but transforms result remotely.
     *
     * @param rmtTransform Remote transformer.
     * @param args Optional arguments.
     * @return Future for the query result.
     */
    public <R> GridCacheQueryFuture<R> execute(GridClosure<T, R> rmtTransform, @Nullable Object... args);

    /**
     * Gets metrics for this query.
     *
     * @return Query metrics.
     */
    public GridCacheQueryMetrics metrics();

    /**
     * Resets metrics for this query.
     */
    public void resetMetrics();
}
