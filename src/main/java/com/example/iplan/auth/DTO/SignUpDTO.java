package com.example.iplan.auth.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class SignUpDTO {
    private String email;
    private String password;
    private String name;
    private String authority;
}
