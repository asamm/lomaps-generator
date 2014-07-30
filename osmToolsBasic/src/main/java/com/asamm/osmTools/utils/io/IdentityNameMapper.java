package com.asamm.osmTools.utils.io;

/**
 * NOP implementation of the name mapper.
 *
 * @author Rein Raudjärv
 *
 * @see NameMapper
 */
final class IdentityNameMapper implements NameMapper {

    public static final NameMapper INSTANCE = new IdentityNameMapper();

    private IdentityNameMapper() {
    }

    @Override
    public String map(String name) {
        return name;
    }
}
