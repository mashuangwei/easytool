package com.msw.entity;

import lombok.Data;
import org.apache.http.Header;

/**
 * @author mashuangwei
 * @date 2018-11-06 16:17
 * @description:
 */
@Data
public class ResultResponse {

    private String cookie;
    private int statusCode;
    private Header[] headers;
    private String responseContent;

}
