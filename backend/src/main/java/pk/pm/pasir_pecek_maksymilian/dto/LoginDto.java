package pk.pm.pasir_pecek_maksymilian.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDto {

    @NotBlank(message = "Email nie może być pusty")
    private String email;

    @NotBlank(message = "Hasło nie może być puste")
    private String password;
}