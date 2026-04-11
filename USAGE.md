# 📖 Usage guide

<p align="center">
  English | <a href="USAGE_ru.md">Русский</a>
</p>

## 1. Install the app

- **Build and install** the app on your Android device  
  (you can also use a ready-made APK from the [releases](./releases)). All releases are built automatically using GitHub Actions.

---

## 2. Configure the app

1. Open the app and go to **settings**.
2. Select **Add bot to group**. After that Telegram will open and you will be able to choose which chat to add the bot to. When adding, do not forget to give the bot all administrator rights. If the bot is added successfully, you will see a message about the app being authorized in your group via the bot.
3. *(if necessary)* Choose the desired **sync interval** for checking new files in the folders you selected on your phone.
4. *(if necessary)* Choose **Sync settings**. This allows you to select two synchronization options: Wi‑Fi + mobile data or Wi‑Fi only.
5. *(if necessary)* Choose the **app theme**.

---

## 3. Add a folder for synchronization

1. On the main screen tap the **"+ Add folder"** button.
2. Enter the **folder name**.
3. **Select a folder** on your device from which you want to upload files.
4. *(if necessary)* Specify the **topic ID** if you need a specific topic.
5. Choose the type of media to send from the selected folder on your phone: Photos, Videos, or All.
6. Save the folder settings.

---

## 4. Start synchronization

- On the main screen tap the **"Start"** button.
- The app will start scanning the selected folders and uploading new media to the specified Telegram chat.

---

## 5. Additional explanations

1. If you change your mind about sending files from your phone to the selected topic in Telegram, you can choose another topic ID, tap **Reset sent files cache** (the icon next to the trash bin), and all files will be resent to the new topic. This also works if you accidentally deleted synchronized files from Telegram – just tap **Reset sent files cache** and they will be sent to the chat again.
2. If there are files in your folder that you do not want to send to Telegram, simply tap **preview** and that file will not be sent.
3. If the messages in the chat disturb you, simply tap **Clear log**.
4. If you select a non‑existent topic ID, the app will stop its work and highlight the misconfigured folder in red.

---

## 6. Optional settings 

6.1. Configure the Telegram bot

1. **Create a new bot** in Telegram using [@BotFather](https://t.me/BotFather) and get its token.
2. **Enter the token** of your bot in the app settings, and then authorize it according to section 2 of this guide.

*P.S. If your group has **topics**, you need to find out the topic ID for sending. To do this, open the topic settings of the chat and you will see a link like **t.me/c/XXXXXXXXXX/7**, where 7 (shown as an example) is the topic ID.*
