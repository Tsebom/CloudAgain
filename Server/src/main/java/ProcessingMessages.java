public class ProcessingMessages implements Runnable{
    private final User user;
    private final String messages;

    public ProcessingMessages(User user, String messages) {
        this.user = user;
        this.messages = messages;
    }

    @Override
    public void run() {
        StringBuilder sb = new StringBuilder();
        sb.append(user.getStorageMessage()).append(messages);

        String msg = sb.toString().trim();
        String[] s = msg.split("_");

        if (s[0].equals("start") & s[s.length - 1].equals("end")) {
            user.processing(msg.substring(1, msg.length() - 2));
        } else {
            user.setStorageMessage(msg);
        }
    }
}
