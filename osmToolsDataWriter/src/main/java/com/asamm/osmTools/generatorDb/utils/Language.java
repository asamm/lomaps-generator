package com.asamm.osmTools.generatorDb.utils;

/**
 * Created by voldapet on 16/1/2018.
 */

public enum Language {

    ARABIC("ar", "\u200E العربية", "Arabic"),
    CZECH("cs", "Čeština","Czech"),
    DANISH("da", "Dansk","Danish"),
    GERMAN("de", "Deutsch","German"),
    GREEK("el", "Ελληνικά","Greek"),
    ENGLISH("en", "English","English"),
    SPANISH("es", "Español","Spanish"),
    ESTONIAN("et", "Eesti keel","Estonian"),
    FINNISH("fi", "Suomi","Finnish"),
    FRENCH("fr", "Français","French"),
    HUNGARIAN("hu", "Magyar","Hungarian"),
    ITALIAN("it", "Italiano","Italian"),
    JAPANESE("ja", "日本語","Japanese"),
    KOREAN("ko", "한국어","Korean"),
    DUTCH("nl", "Nederlands","Dutch"),
    NORWEGIAN("nb", "Norsk Bokmål","Norwegian"),
    POLISH("pl", "Polski","Polish"),
    PORTUGUESE("pt", "Português","Portuguese"),
    RUSSIAN("ru", "Русский","Russian"),
    SLOVAK("sk", "Slovenčina","Slovak"),
    SLOVENIAN("sl", "Slovenščina","Slovenian"),
    UKRAINIAN("uk", "Українська","Ukrainian"),
    CHINESE("zh", "簡體中文","Chinese, Simplified")

    ;

    /**
     * Find Language enum in list of defined values.
     * @param code code of language to "create"
     * @return Language for such code or null if language with this code is not defined
     */
    public static Language fromLangCode(String code) {

        if (code == null){
            return null;
        }

        for (Language lang : Language.values()) {
            if (lang.code.equals(code)) {
                return lang;
            }
        }
        // lang for this code is not defined
        return null;
    }


    /*
     * ISO 639-1 language code
     */
    String code;

    /*
     * Language name in local language
     */
    String name;

    /*
     * Language name in English
     */
    String nameEn;

    /**
     * Create language enum
     * @param code ISO 639-1 language code
     * @param name local language name
     * @param nameEn name of language in English
     */
    private Language(String code, String name, String nameEn){
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
    }

    /**
     * Get ISO 639-1 language code
     * @return
     */
    public String getCode (){
        return code;
    }

    /**
     * Get name of language in English
     * @return
     */
    public String getNameEn () {
        return nameEn;
    }

    /**
     * Get local name of language
     * @return local name of language
     */
    public String getName () {
        return name;
    }

    @Override
    public String toString() {
        return this.getCode();
    }
}
