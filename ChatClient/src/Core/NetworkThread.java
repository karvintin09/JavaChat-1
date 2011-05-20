package Core;

import UI.ChatUI;
import UI.ContactRequestUI;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

public class NetworkThread implements Runnable, Opcode
{
    private static volatile Thread thread;
    private static Timer timer;
    private int counter;
    
    public static void stop()
    {
        timer.cancel();
        
        thread = null;
        timer = null;
    }
    
    public void run()
    {
        thread = Thread.currentThread();
        
        // The server will first send SMSG_CONTACT_DETAIL signal to inform client that this is a client detail data.
        NetworkManager.getContactList();
        
        try
        {
            Packet p;
            
            while(true)
            {
                p = NetworkManager.ReceivePacket();
                
                if (p.getOpcode() != SMSG_CONTACT_DETAIL)
                    break;
                
                int guid = (Integer)p.get();
                String c_username = (String)p.get();
                String c_title = (String)p.get();
                String c_psm = (String)p.get();
                int c_status = (Integer)p.get();
                
                Contact c = new Contact(guid, c_username, c_title, c_psm, c_status);
                
                UIManager.getMasterUI().addContact(c);
            }
            
            // The server will send SMSG_CONTACT_LIST_ENDED signal to inform client that all client data is sent.
            // If the client receive signal other than SMSG_CONTACT_LIST_ENDED, the client may miss some contact data while receiving.
            if (p.getOpcode() != SMSG_CONTACT_LIST_ENDED)
                UIManager.showMessageDialog("Fail to load contact list, your contact list may incomplete.", "Error", JOptionPane.WARNING_MESSAGE);
            
            // Tell the server the current status of client. Will be useful in login as this status when it is implemented.
            Packet statusPacket = new Packet(CMSG_STATUS_CHANGED);
            statusPacket.put(0);
            
            NetworkManager.SendPacket(statusPacket);
            
            timer = new Timer();
            timer.scheduleAtFixedRate(new PeriodicTimeSyncResp(), 0, 10 * 1000);
            
            while(thread == Thread.currentThread())
            {
                p = NetworkManager.ReceivePacket();
                
                switch (p.getOpcode())
                {
                    case SMSG_SEND_CHAT_MESSAGE:
                        HandleChatMessageOpcode(p);
                        break;
                    case SMSG_STATUS_CHANGED:
                        HandleStatusChangedOpcode(p);
                        break;
                    case SMSG_CONTACT_ALREADY_IN_LIST:
                        UIManager.showMessageDialog("The contact is already in list.", "Add Contact", JOptionPane.INFORMATION_MESSAGE);
                        break;
                    case SMSG_CONTACT_NOT_FOUND:
                        UIManager.showMessageDialog("No such user found.", "Add Contact", JOptionPane.INFORMATION_MESSAGE);
                        break;
                    case SMSG_ADD_CONTACT_SUCCESS:
                        HandleAddContactSuccessOpcode(p);
                        break;
                    case SMSG_CONTACT_REQUEST:
                        HandleContactRequestOpcode(p);
                        break;
                    case SMSG_PING:
                        NetworkManager.SendPacket(new Packet(CMSG_PING));
                        break;
                    case SMSG_TITLE_CHANGED:
                    case SMSG_PSM_CHANGED:
                        HandleContactDetailChangedOpcode(p);
                        break;
                    case SMSG_LOGOUT_COMPLETE:
                        NetworkManager.logout();
                        break;
                }
            }
        }
        catch (SocketException se)
        {
            NetworkManager.logout();
            
            UIManager.showMessageDialog("You have been disconnected from the server.", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (SocketTimeoutException ste)
        {
            NetworkManager.logout();
            
            UIManager.showMessageDialog("You have been disconnected from the server.", "Disconnected", JOptionPane.INFORMATION_MESSAGE);
        }
        catch (Exception e) {}
    }
    
    void HandleChatMessageOpcode(Packet packet)
    {
        int senderGuid = (Integer)packet.get();
        String message = (String)packet.get();
        
        Contact s_contact = null;
        
        // Search contact list have this contact detail or not.
        // This help the client to deny chat message if the contact is deleted.
        s_contact = UIManager.getMasterUI().searchContact(senderGuid);
        
        // Cant find sender contact detail in list. Possible deleted.
        if (s_contact == null)
            return;
        
        ChatUI targetUI = UIManager.getChatUIList().findUI(s_contact);
        
        if (targetUI == null)
            UIManager.getChatUIList().add(targetUI = new ChatUI(s_contact, UIManager.getMasterUI().getAccountDetail().getTitle()));
        
        // Output the message in sender ChatUI.
        targetUI.append(s_contact.getTitle(), message);
        targetUI.toFront();
    }
    
    void HandleStatusChangedOpcode(Packet packet)
    {
        int guid = (Integer)packet.get();
        int status = (Integer)packet.get();
        
        UIManager.UpdateContactStatus(guid, status);
    }
    
    void HandleAddContactSuccessOpcode(Packet packet)
    {
        int guid = (Integer)packet.get();
        String username = (String)packet.get();
        String title = (String)packet.get();
        String psm = (String)packet.get();
        int c_status = (Integer)packet.get();
       
        Contact c = new Contact(guid, username, title, psm, c_status);
       
        UIManager.getMasterUI().addContact(c);
    }

    void HandleContactRequestOpcode(Packet packet)
    {
        int r_guid = (Integer)packet.get();
        String r_username = (String)packet.get();
        
        new ContactRequestUI(r_guid, r_username);
    }
    
    void HandleContactDetailChangedOpcode(Packet packet)
    {
        int guid = (Integer)packet.get();
        String data = (String)packet.get();
        
        if (packet.getOpcode() == SMSG_TITLE_CHANGED)
            UIManager.getMasterUI().UpdateContactDetail(guid, data, null);
        else if (packet.getOpcode() == SMSG_PSM_CHANGED)
            UIManager.getMasterUI().UpdateContactDetail(guid, null, data);
    }
    
    class PeriodicTimeSyncResp extends TimerTask 
    {
        public PeriodicTimeSyncResp()
        {
            counter = 0;
        }
        
        public void run() 
        {
            Packet p = new Packet(CMSG_TIME_SYNC_RESP);
            p.put(counter++);
            p.put(System.currentTimeMillis());
            
            NetworkManager.SendPacket(p);
        }
    }
}
