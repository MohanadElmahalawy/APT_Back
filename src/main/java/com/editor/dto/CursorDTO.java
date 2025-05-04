package com.editor.dto;

import lombok.Data;

@Data
public class CursorDTO {
    private String userId;
    private String userName;
    private int line;
    private int column;
}