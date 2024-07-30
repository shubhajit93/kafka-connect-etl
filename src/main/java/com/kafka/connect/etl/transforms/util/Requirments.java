//package com.kafka.connect.etl.transforms.util;
package com.kafka.connect.etl.transforms.util;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;

public class Requirments {


    public static Struct requireStruct(Object value, String purpose){
        if(!(value instanceof Struct)){
            throw new DataException("Only Struct objects supported for [" + purpose + "], found: " + nullSafeClassName(value));
        }
        return (Struct) value;
    }

    private static String nullSafeClassName(Object value) {
        return value == null ? "null":value.getClass().getName();
    }

    public static Struct requireStructOrNull(Object value, String purpose){
        if(value == null){
            return null;
        }
        return requireStruct(value, purpose);
    }
}
