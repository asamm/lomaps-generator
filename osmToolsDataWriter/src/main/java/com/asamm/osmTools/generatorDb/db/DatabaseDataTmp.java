package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.set.hash.TLongHashSet;
import locus.api.utils.DataReaderBigEndian;
import locus.api.utils.DataWriterBigEndian;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.store.DataInputStoreReader;
import org.openstreetmap.osmosis.core.store.DataOutputStoreWriter;
import org.openstreetmap.osmosis.core.store.DynamicStoreClassRegister;

import javax.naming.directory.InvalidAttributesException;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.asamm.locus.features.loMaps.LoMapsDbConst.*;

/**
 * Created by voldapet on 8/10/15.
 */
public class DatabaseDataTmp extends ADatabaseHandler {

    private static final String TAG = DatabaseDataTmp.class.getSimpleName();


    private PreparedStatement psInsertNode;
    private PreparedStatement psInsertWay;
    private PreparedStatement psInsertRelation;
    private PreparedStatement psInsertWayStreet;
    private PreparedStatement psInsertWayStreetByOsmId;

    private PreparedStatement psSelectNode;
    private PreparedStatement psSelectWay;
    private PreparedStatement psSelectRelation;
    private PreparedStatement psSelectWayStreets;
    private PreparedStatement psSelectWayStreetsByOsmId;

     // dynamic register for database
    private ByteArrayOutputStream baos;
    private DataOutputStoreWriter dosw;
    private DynamicStoreClassRegister dynamicRegister;

    private DataWriterBigEndian dwbe;
    private DataReaderBigEndian drbe;


    int nodeInsertBatchSize = 0;
    int wayInsertBatchSize = 0;
    int relationInsertBatchSize = 0;
    int streetInsertBatchSize = 0;
    int waystreetByOsmIdInsertBatchSize = 0;

    public DatabaseDataTmp(File file, boolean deleteExistingDb)
            throws Exception {
        super(file, deleteExistingDb);

        setTables();

        // prepare statements
        psInsertNode = createPreparedStatement("INSERT INTO nodes (id, data) VALUES (?, ?)");
        psInsertWay = createPreparedStatement("INSERT INTO ways (id, data) VALUES (?, ?)");
        psInsertRelation = createPreparedStatement("INSERT INTO relations (id, data) VALUES (?, ?)");
        psInsertWayStreet = createPreparedStatement("INSERT INTO Streets (hash, data) VALUES (?, ?)");
        psInsertWayStreetByOsmId = createPreparedStatement("INSERT INTO Waystreets (id, data) VALUES (?, ?)");

        psSelectNode = createPreparedStatement("SELECT data FROM nodes WHERE id=?");
        psSelectWay = createPreparedStatement("SELECT data FROM ways WHERE id=?");
        psSelectRelation = createPreparedStatement("SELECT data FROM relations WHERE id=?");
        psSelectWayStreets = createPreparedStatement("SELECT data from Streets where hash=?");
        psSelectWayStreetsByOsmId = createPreparedStatement("SELECT data from Waystreets where id=?");

        baos = new ByteArrayOutputStream();
        dosw = new DataOutputStoreWriter(new DataOutputStream(baos));
        dynamicRegister = new DynamicStoreClassRegister();


        dwbe = new DataWriterBigEndian();
    }

    @Override
    protected void cleanTables() {
        // nothing to do this table is temporary and it's always create as new

    }


    @Override
    protected void setTables() throws SQLException, InvalidAttributesException {
        String sql = "CREATE TABLE nodes (";
        sql += "id BIGINT NOT NULL PRIMARY KEY,";
        sql += "data BLOB NOT NULL)";
        Statement stmt = conn.createStatement();
        stmt.execute(sql);

        sql = "CREATE TABLE ways (";
        sql += "id BIGINT NOT NULL PRIMARY KEY,";
        sql += "data BLOB NOT NULL)";
        stmt.execute(sql);

        sql = "CREATE TABLE relations (";
        sql += "id BIGINT NOT NULL PRIMARY KEY,";
        sql += "data BLOB NOT NULL)";
        stmt.execute(sql);

        sql = "CREATE TABLE "+TN_STREETS+" (";
        sql += COL_HASH+" INT NOT NULL ,";
        sql += COL_DATA + " BLOB";
        sql +=        " )";
        stmt.execute(sql);

        sql = "CREATE TABLE Waystreets ( ";
        sql += "id BIGINT NOT NULL PRIMARY KEY, ";
        sql += COL_DATA + " BLOB";
        sql +=        " )";
        stmt.execute(sql);

        stmt.close();
    }

    public void createWayStreetIndex() {
        try {
            // finalize inserts of streets
            psInsertWayStreet.executeBatch();
            streetInsertBatchSize = 0;
            // test if index exist
            String sql = "SELECT * FROM sqlite_master where name = 'idx_streets_hash'";
            ResultSet rs = getStmt().executeQuery(sql);

            if (rs.next()){
                sql = "REINDEX idx_streets_hash ";
                executeStatement(sql);
            }
            else{
                sql = "CREATE INDEX idx_streets_hash ON " + TN_STREETS + " (" + COL_HASH+  ")";
                executeStatement(sql);
            }

        } catch (SQLException e) {
            Logger.e(TAG, "createWayStreetIndex(), problem with query", e);
        }
    }


    public void dropWayStreetIndex() {
        try {

            commit(false);
            //restartConnection(); // nasty workaround how to solve locked tables
            String sql = "DROP INDEX IF EXISTS  idx_streets_hash";
            executeStatement(sql);
        } catch (SQLException e) {
            Logger.e(TAG, "dropWayStreetIndex(), problem with query", e);
        }
    }

    public void destroy() throws SQLException {

        super.destroy();

        // delete the tmp database file itself
        dbFile.delete();
    }

    public void insertNode(long id, Entity entity) {

        try {
            psInsertNode.setLong(1, id);
            psInsertNode.setBytes(2, serializeEntity(entity));
            psInsertNode.addBatch();

            nodeInsertBatchSize++;
            if (nodeInsertBatchSize % 1000 == 0){
                psInsertNode.executeBatch();
                nodeInsertBatchSize = 0;
            }

        } catch (SQLException e) {
            Logger.e(TAG, "insertNode(), problem with query", e);
        }
    }

    public Node selectNode (long id){

        try {

            psSelectNode.clearParameters();
            psSelectNode.setLong(1, id);

            ResultSet rs = psSelectNode.executeQuery();

            if (rs.next()) {
                byte[] nodeData = rs.getBytes(1);
                ByteArrayInputStream bais = new ByteArrayInputStream(nodeData);
                DataInputStream dis = new DataInputStream(bais);
                DataInputStoreReader disr = new DataInputStoreReader(dis);
                Node node = new Node(disr, dynamicRegister);
                dis.close();
                return node;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<Node> selectNodes(long[] ids) {

        List<Node> nodes = new ArrayList<>();
        LinkedHashMap<Long, Node> resultMap = new LinkedHashMap<>();

        String sql = "SELECT data FROM nodes WHERE id IN ";
        StringBuilder isInIds = new StringBuilder("(");
        for (int i=0; i < ids.length; i++){
            if (i==0){
                isInIds.append(ids[i]);
            }
            else {
                isInIds.append(",").append(ids[i]);
            }
        }
        isInIds.append(")");

        sql += isInIds.toString();

        ResultSet rs = null;
        try {
            rs = getStmt().executeQuery(sql);
            for (int i=0; rs.next(); i++) {
                byte[] nodeData = rs.getBytes(1);
                ByteArrayInputStream bais = new ByteArrayInputStream(nodeData);
                DataInputStream dis = new DataInputStream(bais);
                DataInputStoreReader disr = new DataInputStoreReader(dis);
                Node node = new Node(disr, dynamicRegister);
                dis.close();

                if (node == null){
                    Logger.i(TAG, "Can not create node with id; SQL: " + sql);
                    continue;
                }
                resultMap.put(node.getId(), node);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // return the nodes in correct order
        for (int i=0; i < ids.length; i++){
            Node node = resultMap.get(ids[i]);
            if (node != null){
                nodes.add(node);
            }
        }
        return nodes;
    }

    public void insertWay(long id, Entity entity) {

        try {
            psInsertWay.setLong(1,id);
            psInsertWay.setBytes(2, serializeEntity(entity));
            psInsertWay.addBatch();

            wayInsertBatchSize++;

            if (wayInsertBatchSize % 1000 == 0){
                psInsertWay.executeBatch();
                wayInsertBatchSize = 0;
            }
        } catch (SQLException e) {
            Logger.e(TAG, "insertWay(), problem with query", e);
        }
    }

    public Way selectWay (long id){

        try {

            psSelectWay.clearParameters();
            psSelectWay.setLong(1, id);

            ResultSet rs = psSelectWay.executeQuery();
            if (rs.next()) {
                byte[] nodeData = rs.getBytes(1);
                ByteArrayInputStream bais = new ByteArrayInputStream(nodeData);
                DataInputStream dis = new DataInputStream(bais);
                DataInputStoreReader disr = new DataInputStoreReader(dis);
                Way way = new Way(disr, dynamicRegister);
                dis.close();
                return way;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void insertRelation(long id, Entity entity) {

        try {
            psInsertRelation.setLong(1,id);
            psInsertRelation.setBytes(2, serializeEntity(entity));

            psInsertRelation.addBatch();
            relationInsertBatchSize++;

            if (relationInsertBatchSize % 1000 == 0){
                psInsertRelation.executeBatch();
                relationInsertBatchSize = 0;
            }
        } catch (SQLException e) {
            Logger.e(TAG, "insertRelation(), problem with query", e);
        }
    }

    public Relation selectRelation (long id){

        try {

            psSelectRelation.clearParameters();
            psSelectRelation.setLong(1, id);

            ResultSet rs = psSelectRelation.executeQuery();

            if (rs.next()) {
                byte[] data = rs.getBytes(1);
                ByteArrayInputStream bais = new ByteArrayInputStream(data);
                DataInputStream dis = new DataInputStream(bais);
                DataInputStoreReader disr = new DataInputStoreReader(dis);
                Relation relation = new Relation(disr, dynamicRegister);
                dis.close();
                return relation;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public void insertWayStreet(int hash, Street street) {

        try {
            psInsertWayStreet.setInt(1, hash);
            psInsertWayStreet.setBytes(2, street.getAsBytes());
            psInsertWayStreet.addBatch();

            streetInsertBatchSize++;
            if (streetInsertBatchSize % 1000 == 0){
                psInsertWayStreet.executeBatch();
                streetInsertBatchSize = 0;
            }

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreetToCache(), problem with query", e);
        }
    }

    /**
     * Load streets from cache by hash code from street name
     * @param hash
     * @return
     */
    public List<Street> selectWayStreets(int hash){

        List<Street> loadedStreets = new ArrayList<>();
        try {

            psSelectWayStreets.clearParameters();
            psSelectWayStreets.setInt(1, hash);

            ResultSet rs = psSelectWayStreets.executeQuery();

            while (rs.next()) {
                Street street = new Street();
                street.read(rs.getBytes(1));
                loadedStreets.add(street);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return loadedStreets;
    }

    /**
     * Load waystreet by osm ids
     * @param  osmIds
     * @return
     */
    public List<Street> selectWayStreetsNamed(List<Long> osmIds){

        List<Street> streets = new ArrayList<>();

        boolean isTest = false;

        String sql = "SELECT data FROM Streets WHERE id IN ";
        StringBuilder isInIds = new StringBuilder("(");
        for (int i = 0, size = osmIds.size(); i < size; i++){

            if (osmIds.get(i) ==  30172365){
                Logger.i(TAG, " -- selectWayStreetsNamed(): is test true");
                isTest = true;
            }

            if (i==0){
                isInIds.append(osmIds.get(i));
            }
            else {
                isInIds.append(",").append(osmIds.get(i));
            }
        }
        isInIds.append(")");

        sql += isInIds.toString();

        ResultSet rs = null;
        try {
            rs = getStmt().executeQuery(sql);
            for (int i=0; rs.next(); i++) {
                byte[] data = rs.getBytes(1);

                if (data == null){
                    Logger.i(TAG, " selectWayStreetsNamed(): Can not create unnamed street with id; SQL: " + sql);
                    continue;
                }
                Street street = new Street();
                street.read(data);
                streets.add(street);
            }

        } catch (Exception e) {
            Logger.e(TAG, "selectWayStreetsNamed(), problem with query", e);
        }

        if (isTest){
            for (Street street : streets){
                Logger.i(TAG, " selectWayStreetsNamed(): loaded streets: " + street.toString());
            }
        }

        return streets;
    }

    /**
     * IMPORTANT - delete all wayStreet from DB
     */
    public void deleteWayStreetData() {

        try {
            String sql = "DELETE FROM "+TN_STREETS;
            executeStatement(sql);

        } catch (SQLException e) {
            Logger.e(TAG, "deleteWayStreetData(), problem with query", e);
        }
    }


    // INSERT SELECT UNNAMED WAYSTREETS

    public void insertWayStreetByOsmId(Street street) {

        try {
            psInsertWayStreetByOsmId.setLong(1, street.getOsmId());
            psInsertWayStreetByOsmId.setBytes(2, street.getAsBytes());
            psInsertWayStreetByOsmId.addBatch();

            waystreetByOsmIdInsertBatchSize++;
            if (waystreetByOsmIdInsertBatchSize % 1000 == 0){
                psInsertWayStreetByOsmId.executeBatch();
                waystreetByOsmIdInsertBatchSize = 0;
            }

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreetByOsmId(), problem with query", e);
        }
    }


    public Street selectWayStreetByOsmId(long id){

        try {
            psSelectWayStreetsByOsmId.clearParameters();
            psSelectWayStreetsByOsmId.setLong(1, id);

            ResultSet rs = psSelectWayStreetsByOsmId.executeQuery();

            while (rs.next()) {
                Street street = new Street();
                street.read(rs.getBytes(1));
                return street;
            }
        } catch (Exception e) {
            Logger.e(TAG, "selectWayStreetByOsmId(), problem with query", e);
        }
        return null;
    }

    /**
     * Select waystreets based on osmIds. It can contains waystreets with name but also streets without name
     * @param ids ids of streets to select
     * @return list of loaded streets from tmp database
     */
    public List<Street> selectWayStreetsByOsmIds(List<Long> ids){

        List<Street> streets = new ArrayList<>();

        String sql = "SELECT data FROM Waystreets WHERE id IN ";
        StringBuilder isInIds = new StringBuilder("(");
        for (int i = 0, size = ids.size(); i < size; i++){
            if (i==0){
                isInIds.append(ids.get(i));
            }
            else {
                isInIds.append(",").append(ids.get(i));
            }
        }
        isInIds.append(")");

        sql += isInIds.toString();

        ResultSet rs = null;
        try {
            rs = getStmt().executeQuery(sql);
            for (int i=0; rs.next(); i++) {
                byte[] data = rs.getBytes(1);

                if (data == null){
                    Logger.i(TAG, " selectWayStreetsByOsmIds(): Can not create waystreet with id; SQL: " + sql);
                    continue;
                }
                Street street = new Street();
                street.read(data);
                streets.add(street);
            }

        } catch (Exception e) {
            Logger.e(TAG, "selectWayStreetsByOsmIds(), problem with query", e);
        }
        return streets;
    }

    // SERIALIZATION

    private byte[] serializeEntity (Entity entity){

        baos.reset();
        entity.store(dosw, dynamicRegister);
        try {
            baos.flush();
        } catch (IOException e) {
            Logger.e(TAG, "serializeEntity(), problem with serialization of entity:" + entity.toString(), e);
            return null;
        }

        return baos.toByteArray();
    }


    /**
     * Execute not finished batch statemetn
     */
    public void finalizeBatchStatement() {

        try {
            psInsertNode.executeBatch();
            nodeInsertBatchSize = 0;

            psInsertWay.executeBatch();
            wayInsertBatchSize = 0;

            psInsertRelation.executeBatch();
            relationInsertBatchSize = 0;
        } catch (SQLException e) {
            Logger.e(TAG, "finalizeBatchStatement(), problem with query", e);
        }
    }


    // SERIALIZATION PART


    private byte[] serializeStreet(Street street){
        try {
            dwbe.reset();

            dwbe.writeLong(street.getCityId());
            //dwbe.writeLong(street.getCityPartId());
            dwbe.writeString(street.getName());
            // write list of city ids
            TLongHashSet cityIds = street.getCityIds();
            dwbe.writeInt(cityIds.size());
            TLongIterator iterator = cityIds.iterator();
            while (iterator.hasNext()){
                dwbe.writeLong(iterator.next());
            }

            byte[] geomData = wkbWriter.write(street.getGeometry());
            dwbe.writeInt(geomData.length);
            dwbe.write(geomData);

            return dwbe.toByteArray();
        } catch (IOException e) {
            Logger.e(TAG, "serializeStreet(), problem with serialization of street:" + street.toString(), e);
            e.printStackTrace();
            return null;
        }
    }


    private Street readStreet (byte[] data){
        try {
            drbe = new DataReaderBigEndian(data);
            Street street = new Street();
            street.setCityId(drbe.readLong());
            street.setName(drbe.readString());
            //read list of cityIds
            int size = drbe.readInt();
            TLongHashSet cityIds = new TLongHashSet();
            for (int i=0; i < size; i++){
                cityIds.add(drbe.readLong());
            }
            street.setCityIds(cityIds);

            int count = drbe.readInt();
            street.setGeometry((com.vividsolutions.jts.geom.MultiLineString) wkbReader.read(drbe.readBytes(count)));

            return  street;

        } catch (Exception e) {
            Logger.e(TAG, "readStreet(), problem with serialization of street", e);
            e.printStackTrace();
            return new Street();
        }
    }



}
