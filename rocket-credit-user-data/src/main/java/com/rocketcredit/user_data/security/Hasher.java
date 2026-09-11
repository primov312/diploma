package com.rocketcredit.user_data.security;

import org.mindrot.jbcrypt.BCrypt;

public class Hasher {

    public static String hash(String id) {
        String salt = BCrypt.gensalt(12);
        return BCrypt.hashpw(id, salt);
    }

    public static String hash(long id) {
        String strId = String.valueOf(id);
        String salt = BCrypt.gensalt(12);
        return BCrypt.hashpw(strId, salt);
    }
}