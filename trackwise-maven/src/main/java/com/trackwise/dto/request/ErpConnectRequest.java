package com.trackwise.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpConnectRequest {
    private String clientId;
    private String clientSecret;
    private String companyId;
    private String authCode;
}
