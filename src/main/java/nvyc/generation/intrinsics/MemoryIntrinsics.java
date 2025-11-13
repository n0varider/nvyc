package nvyc.generation.intrinsics;

import nvyc.data.NodeType;
import nvyc.data.Symbols;
import nvyc.data.VariableData;

import java.util.Map;

public class MemoryIntrinsics {

    private VariableData vardata = VariableData.getInstance();

    /*

        AUX
        type = STR      aux = string literal
        type = STRUCT   aux = struct name
        type = ARRAY    aux = array name (for VLAs, return size 8)

     */
    public int sizeof(NodeType type, Object aux) {
        int size = Symbols.sizeof(type);

        if(size != -1) return size;

        else {
            switch(type) {
                case STR:
                    if(aux == null) return 8;
                    return aux.toString().length();
                case STRUCT:
                    String struct = aux.toString();
                    Map<String, Object> types = vardata.getStructTypes(struct);
                    int totalSize = 0;

                    // TODO will not work if a struct contains another struct. This hasn't been implemented yet
                    for(String s : types.keySet()) {
                        totalSize += sizeof((NodeType) types.get(s), null);
                    }

                    return totalSize;

                case ARRAY:
                    return 0; // TODO temporary
                default:
                    return -1;

            }
        }
    }

    /*

        Can be mapped to something like
        %ptr = getelementptr %struct, %struct* null, i32 0, i32 [member_index]
        %offset = ptrtoint %struct* %ptr to i64

        From stddef.h (though this version seems to no longer be used for corner cases)
        #define offsetof(st, m) ((size_t)&(((st*)0)->m))

     */
    public int offsetof(String struct, String member) {
        return -1; // TODO temporary
    }
}
