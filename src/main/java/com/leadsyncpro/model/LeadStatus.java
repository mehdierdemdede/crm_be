package com.leadsyncpro.model;

public enum LeadStatus {
    /** Henüz temas kurulmamış lead */
    UNCONTACTED,

    /** Sıcak hasta — 1 hafta içinde satış olmazsa uyarı gider */
    HOT,

    /** Satış gerçekleşmiş lead — satış formu (popup) açılır */
    SOLD,

    /** İlgisiz lead — ertesi gün Super User’a aktarılır */
    NOT_INTERESTED,

    /** Engellenmiş lead — ertesi gün Super User’a aktarılır */
    BLOCKED,

    /** Yanlış bilgi içeren lead — ertesi gün Super User’a aktarılır */
    WRONG_INFO
}
