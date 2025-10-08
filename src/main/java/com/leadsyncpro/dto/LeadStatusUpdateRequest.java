package com.leadsyncpro.dto;

import com.leadsyncpro.model.LeadStatus;
import lombok.Data;

@Data
public class LeadStatusUpdateRequest {
    /**
     * Yeni durum değeri. Backend tarafında, istek gövdesi zorunlu olmadığı için
     * değer null olabilir ve bu durumda alternatif parametreler devreye girer.
     */
    private LeadStatus status;
}
