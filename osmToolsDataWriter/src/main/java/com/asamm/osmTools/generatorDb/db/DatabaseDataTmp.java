package com.asamm.osmTools.generatorDb.db;

import com.asamm.osmTools.generatorDb.address.Street;
import com.asamm.osmTools.utils.Logger;
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
import java.util.List;

import static com.asamm.locus.features.dbPoi.DbPoiConst.*;

/**
 * Created by voldapet on 8/10/15.
 */
public class DatabaseDataTmp extends ADatabaseHandler {

    private static final String TAG = DatabaseDataTmp.class.getSimpleName();


    private PreparedStatement psInsertNode;
    private PreparedStatement psInsertWay;
    private PreparedStatement psInsertRelation;
    private PreparedStatement psInsertStreet;

    private PreparedStatement psSelectNode;
    private PreparedStatement psSelectWay;
    private PreparedStatement psSelectRelation;
    private PreparedStatement psSelectStreets;

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

    public DatabaseDataTmp(File file, boolean deleteExistingDb)
            throws Exception {
        super(file, deleteExistingDb);

        setTables();

        // prepare statements
        psInsertNode = createPreparedStatement("INSERT INTO nodes (id, data) VALUES (?, ?)");
        psInsertWay = createPreparedStatement("INSERT INTO ways (id, data) VALUES (?, ?)");
        psInsertRelation = createPreparedStatement("INSERT INTO relations (id, data) VALUES (?, ?)");
        psInsertStreet = createPreparedStatement("INSERT INTO Streets (hash, data) VALUES (?, ?)");

        psSelectNode = createPreparedStatement("SELECT data FROM nodes WHERE id=?");
        psSelectWay = createPreparedStatement("SELECT data FROM ways WHERE id=?");
        psSelectRelation = createPreparedStatement("SELECT data FROM relations WHERE id=?");
        psSelectStreets = createPreparedStatement("SELECT data from Streets where hash=?");

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
        sql += COL_HASH+" INT NOT NULL PRIMARY KEY ,";
        sql += COL_DATA + " BLOB";
        sql +=        " )";
        stmt.execute(sql);

        stmt.close();
    }

//    public void createStreetIndex () {
//        try {
//            // finalize inserts of streets
//            psInsertStreet.executeBatch();
//            streetInsertBatchSize = 0;
//
//            String sql = "CREATE INDEX idx_streets_hash ON " + TN_STREETS + " (" + COL_HASH+  ")";
//            executeStatement(sql);
//        } catch (SQLException e) {
//            Logger.e(TAG, "createStreetIndex(), problem with query", e);
//        }
//    }

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


    public void insertStreet(int hash, Street street) {

        try {
            psInsertStreet.setInt(1, hash);
            psInsertStreet.setBytes(2, serializeStreet(street));
            psInsertStreet.addBatch();

            streetInsertBatchSize++;
            if (streetInsertBatchSize % 1000 == 0){
                psInsertStreet.executeBatch();
                streetInsertBatchSize = 0;
            }

        } catch (SQLException e) {
            Logger.e(TAG, "insertWayStreetToCache(), problem with query", e);
        }
    }


    public List<Street> selectStreets(int hash){

        List<Street> loadedStreets = new ArrayList<>();
        try {

            psSelectStreets.clearParameters();
            psSelectStreets.setInt(1, hash);

            ResultSet rs = psSelectStreets.executeQuery();

            while (rs.next()) {
                byte[] data = rs.getBytes(1);
                Street street = readStreet(data);
                loadedStreets.add(street);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return loadedStreets;
    }


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

    private byte[] serializeStreet(Street street){
        try {
            dwbe.reset();

            dwbe.writeLong(street.getCityId());
            //dwbe.writeLong(street.getCityPartId());
            dwbe.writeString(street.getName());
            // write list of city ids
            List<Long> cityIds = street.getCityIds();
            dwbe.writeInt(cityIds.size());
            for (int i=0, size=cityIds.size(); i < size; i++){
                dwbe.writeLong(cityIds.get(i));
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
            List<Long> cityIds = new ArrayList<>();
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

    /**
     * Execute not finished batch statemen
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
}
