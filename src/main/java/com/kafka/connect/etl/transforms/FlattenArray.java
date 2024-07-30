package com.kafka.connect.etl.transforms;

import org.apache.kafka.common.cache.Cache;
import org.apache.kafka.common.cache.LRUCache;
import org.apache.kafka.common.cache.SynchronizedCache;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;
import org.apache.kafka.connect.transforms.util.SchemaUtil;
import org.apache.kafka.connect.transforms.util.SimpleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.kafka.connect.etl.transforms.util.Requirments.requireStructOrNull;


public abstract class FlattenArray<R extends ConnectRecord<R>> implements Transformation<R> {
    public static final String OVERVIEW_DOC = "This transformation is for nested schema as well as array schema transformation";
    private static final String DELIMITER_CONFIG = "delimiter";
    private static final String DELIMITER_DEFAULT = ".";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(DELIMITER_CONFIG, ConfigDef.Type.STRING, DELIMITER_DEFAULT, ConfigDef.Importance.MEDIUM,"Array with Flatten structure");

    private static final String PURPOSE = "flattenArray";
    private String delimiter;
    private Cache<Schema,Schema> schemaUpdateCache;
    @Override
    public R apply(R record) {
        //implement main logic here
        //System.out.println("FlattenArray| record:"+record.toString());
        if(operatingValue(record) == null){
            //System.out.println("FlattenArray| inside operatingValue(record) == null case:"+operatingValue(record));
            return record;
        } else if(operatingSchema(record) == null){
            return null;
        } else {
            System.out.println("FlattenArray| Record:"+record.toString());
            return applyWithSchema(record);
        }

    }

    private R applyWithSchema(R record) {
        final Struct value = requireStructOrNull(operatingValue(record), PURPOSE);
        System.out.println("FlattenArray| Plain-operatingValue:"+value);
        final Schema schema= operatingSchema(record);

        Schema updatedSchema = schemaUpdateCache.get(schema);
        if(updatedSchema == null){
            final SchemaBuilder builder = SchemaUtil.copySchemaBasics(schema, SchemaBuilder.struct());
            Struct defaultValue = (Struct) schema.defaultValue();

            buildUpdatedSchema(schema,"", builder, schema.isOptional(), defaultValue);
            updatedSchema = builder.build();
            schemaUpdateCache.put(schema, updatedSchema);
        }
        if(value == null){
            return newRecord(record, updatedSchema, null);
        } else {
            final Struct updatedValue = new Struct(updatedSchema);
            recordBuildWithSchema(value, "", updatedValue);
            return newRecord(record, updatedSchema, updatedValue);
        }
    }

    private void recordBuildWithSchema(Struct record, String fieldNamePrefix, Struct newRecord) {
        //record=Struct{value=328626450238,count=1,message=Task Id: 1,timestamp=Thu Oct 12 08:45:37 GMT 2023,organizaton=Hishab Technology,
        // services=[IVR, VoiceBridge, AUDIT],location=Dhaka,nested=Struct{address=Gulshan,road=Bashtola_Road}}

        //newRecord: need to be formed for new schema

        if(record == null)
            return;
        for(Field field : record.schema().fields()){
            final String fieldName = fieldName(fieldNamePrefix, field.name());
            switch (field.schema().type()){
                case INT8:
                case INT16:
                case INT32:
                case INT64:
                case FLOAT32:
                case FLOAT64:
                case BOOLEAN:
                case STRING:
                case BYTES:
                    newRecord.put(fieldName, record.get(field.name()));
                    break;
                case STRUCT:
                    recordBuildWithSchema(record.getStruct(field.name()), fieldName, newRecord);
                    break;
                case ARRAY:

                    if(field.schema().valueSchema().type() == Schema.Type.STRUCT){
                        //object array handling
                        //service=[Struct{name=ivr,id=100,attribute=NA}, Struct{name=voice_bridge,id=101,attribute=Not_allowed}]}

                        System.out.println("Inside value struct case");
                        List<Struct> arrayStructs = record.getArray(field.name());

                        List<String> tList = new ArrayList<>();

                        for(Struct arrayStruct : arrayStructs){
                            int indexx = 0;
                            String concateString = "";


                            for(Field structField : arrayStruct.schema().fields()){

                                String keyName = structField.name().toString();
                                String valueName = arrayStruct.get(keyName).toString();
                                String keyValue = keyName+"="+valueName;

                                if(indexx > 0)
                                    concateString = concateString+","+keyValue;
                                else
                                    concateString = concateString+keyValue;

                                indexx++;

                            }

                            tList.add(concateString.toString());
                        }
                        newRecord.put(fieldName, tList);

                    }else{
                        //regular array
                        List<Object>sourceArray = record.getArray(field.name());
                        if(sourceArray != null){
                            System.out.println("FlattenArray| sourceArray:"+sourceArray.toString());
                            List<Object> newArray = new ArrayList<>();
                            Schema elementSchema = field.schema().valueSchema();
                            for(Object sourceElement : sourceArray){
                                if(sourceElement != null){
                                    Object newElement = convertFiled(elementSchema, sourceElement);
                                    newArray.add(newElement);
                                }
                            }
                            System.out.println("FlattenArray| newArray:"+newArray+"| fieldName: "+fieldName);
                            newRecord.put(fieldName, newArray);
                        } else {
                            newRecord.put(fieldName, null);
                        }
                    }

                    break;
                default:
                    throw new DataException("Flatten transformation does not support " + field.schema().type()
                            + " for record with schemas (for field " + fieldName + ").");
            }
        }


    }

    private Object convertFiled(Schema elementSchema, Object sourceElement) {
        if(sourceElement == null)
            return null;

        else if (elementSchema.type() == Schema.Type.INT8
                || elementSchema.type() == Schema.Type.INT16
                || elementSchema.type() == Schema.Type.INT32
                || elementSchema.type() == Schema.Type.INT64
                || elementSchema.type() == Schema.Type.FLOAT32
                || elementSchema.type() == Schema.Type.FLOAT64
                || elementSchema.type() == Schema.Type.BOOLEAN
                || elementSchema.type() == Schema.Type.BYTES
                || elementSchema.type() == Schema.Type.STRING) {
            return sourceElement.toString();
        }
        else {
            throw new DataException("Unsupported element schema type: " + elementSchema.type());
        }

    }

    private void
    buildUpdatedSchema(Schema schema, String fieldNamePrefix, SchemaBuilder newSchema, boolean optional, Struct defaultFromParent) {
        for(Field field: schema.fields()){
            final String fieldName = fieldName(fieldNamePrefix, field.name());
            //final boolean fieldIsOptional = optional || field.schema().isOptional();
            final boolean fieldIsOptional = true;
            Object fieldDefaultValue = null;
            if(field.schema().defaultValue() != null){
                fieldDefaultValue = field.schema().defaultValue();
            } else if(defaultFromParent != null){
                fieldDefaultValue = defaultFromParent.get(field);
            }

            System.out.println("INSITE buildUpdatedSchema function");

            switch (field.schema().type()){
                case INT8:
                    SchemaBuilder int8schema = SchemaBuilder.int8().optional();
                    newSchema.field(fieldName, int8schema.optional());
                    break;
                case INT16:
                    SchemaBuilder int16schema = SchemaBuilder.int16().optional();
                    newSchema.field(fieldName, int16schema.optional());
                    break;
                case INT32:
                    SchemaBuilder int32schema = SchemaBuilder.int32().optional();
                    newSchema.field(fieldName, int32schema.optional());
                    break;
                case INT64:
                    SchemaBuilder int64schema = SchemaBuilder.int64().optional();
                    newSchema.field(fieldName, int64schema.optional());
                    break;
                case FLOAT32:
                    SchemaBuilder float32schema = SchemaBuilder.float32().optional();
                    newSchema.field(fieldName, float32schema.optional());
                    break;
                case FLOAT64:
                    SchemaBuilder float64schema = SchemaBuilder.float64().optional();
                    newSchema.field(fieldName, float64schema.optional());
                    break;
                case BOOLEAN:
                    SchemaBuilder booleanSchema = SchemaBuilder.bool().optional();
                    newSchema.field(fieldName, booleanSchema.optional());
                    break;
                case STRING:
                    SchemaBuilder stringSchema = SchemaBuilder.string().optional();
                    newSchema.field(fieldName, stringSchema.optional());
                    break;
                case BYTES:
                    SchemaBuilder byteSchema = SchemaBuilder.bytes().optional();
                    newSchema.field(fieldName, byteSchema.optional());
                    break;
                case STRUCT:
                    buildUpdatedSchema(field.schema(), fieldName, newSchema, fieldIsOptional, (Struct) fieldDefaultValue);
                    break;
                case ARRAY:

                    SchemaBuilder arraySchema = SchemaBuilder.array(SchemaBuilder.OPTIONAL_STRING_SCHEMA);
                    newSchema.field(fieldName, arraySchema.optional());

                    break;
                default:
                    throw new DataException("Flatten transformation does not support " + field.schema().type()
                            + " for record with schemas (for field " + fieldName + ").");
            }
        }

    }

    private Schema convertFieldSchema(Schema orgSchema, boolean isOptional, Object fieldDefaultValueFromParent) {
        SchemaBuilder builder = SchemaUtil.copySchemaBasics(orgSchema);
        if(isOptional)
            builder.optional();
        if(fieldDefaultValueFromParent != null)
            builder.defaultValue();
        return builder.build();
    }

    private String fieldName(String prefix, String name) {
        return prefix.isEmpty() ? name : (prefix + delimiter + name);
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    protected abstract org.apache.kafka.connect.data.Schema operatingSchema(R record);
    protected abstract Object operatingValue(R record);
    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);


    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> props) {
        System.out.println("FlattenArray| prop:"+props);
        SimpleConfig config = new SimpleConfig(CONFIG_DEF, props);
        delimiter = config.getString(DELIMITER_CONFIG);
        schemaUpdateCache = new SynchronizedCache<>(new LRUCache<>(16));
    }

    public static class Key<R extends ConnectRecord<R>> extends FlattenArray<R>{
        @Override
        protected Schema operatingSchema(R record) {
            return  record.keySchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.key();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), updatedSchema, updatedValue, record.valueSchema(),record.value(), record.timestamp());
        }
    }

    public static class Value<R extends ConnectRecord<R>> extends FlattenArray<R>{
        @Override
        protected Schema operatingSchema(R record) {
            return  record.valueSchema();
        }

        @Override
        protected Object operatingValue(R record) {
            return record.value();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(record.topic(), record.kafkaPartition(), record.keySchema(), record.key(), updatedSchema, updatedValue, record.timestamp());
        }
    }
}
