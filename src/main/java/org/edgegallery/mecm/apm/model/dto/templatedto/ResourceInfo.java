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
public class ResourceInfo {
    private int virtualMemSize;
    private int numVirtualCpu;
    private int sizeOfStorage;
}
