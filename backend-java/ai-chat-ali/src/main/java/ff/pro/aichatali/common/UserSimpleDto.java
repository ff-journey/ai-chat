package ff.pro.aichatali.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author journey
 * @date 2023/3/21
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class UserSimpleDto {
    long id;
    String username;
    String nickname;

    public UserSimpleDto(long id) {
        this.id = id;
    }

    public UserSimpleDto(long id, String username, String nickname) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;

    }
}
