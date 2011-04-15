package Core;

public interface Opcode
{
    final byte CMSG_LOGIN                 = 0x01;
    final byte CMSG_LOGOUT                = 0x02;
    final byte SMSG_LOGIN_SUCCESS         = 0x03;
    final byte SMSG_LOGIN_FAILED          = 0x04;
    final byte SMSG_MULTI_LOGIN           = 0x05;
    final byte CMSG_GET_FRIEND_LIST       = 0x06;
    final byte CMSG_ADD_FRIEND            = 0x07;
    final byte CMSG_REMOVE_FRIEND         = 0x08;
    final byte SMSG_FRIEND_OPCODE_SUCCESS = 0x09;
    final byte CMSG_STATUS_CHANGED        = 0x0A;
}