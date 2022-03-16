package org.edgegallery.mecm.apm.model.dto.templatedto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EdgeResourceInfo {

    public String edge;
    public Resource resource;

}
