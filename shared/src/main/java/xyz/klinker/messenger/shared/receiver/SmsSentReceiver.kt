/*
 * Copyright (C) 2017 Luke Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.klinker.messenger.shared.receiver

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.telephony.SmsManager
import com.klinker.android.send_message.SentReceiver
import com.klinker.android.send_message.StripAccents
import xyz.klinker.messenger.shared.MessengerActivityExtras
import xyz.klinker.messenger.shared.R
import xyz.klinker.messenger.shared.data.ColorSet
import xyz.klinker.messenger.shared.data.DataSource
import xyz.klinker.messenger.shared.data.Settings
import xyz.klinker.messenger.shared.data.model.Message
import xyz.klinker.messenger.shared.service.ResendFailedMessage
import xyz.klinker.messenger.shared.util.*
import java.util.*

/**
 * Receiver for getting notifications of when an SMS has finished sending. By default it's super
 * class will mark the internal message as sent, we need to also mark our database as sent.
 */
open class SmsSentReceiver : SentReceiver() {

    protected open fun retryFailedMessages(): Boolean {
        return true
    }

    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            try {
                super.onReceive(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        Thread {
            try {
                handleReceiver(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun handleReceiver(context: Context, intent: Intent) {
        val uri = Uri.parse(intent.getStringExtra("message_uri"))

        when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE, SmsManager.RESULT_ERROR_NO_SERVICE, SmsManager.RESULT_ERROR_NULL_PDU, SmsManager.RESULT_ERROR_RADIO_OFF -> markMessageError(context, uri)
            else -> try {
                markMessageSent(context, uri)
            } catch (e: Exception) {
                fallbackToLatestMessages(context)
            }

        }
    }

    private fun markMessageSent(context: Context, uri: Uri) {
        markMessage(context, uri, false)
    }

    private fun markMessageError(context: Context, uri: Uri) {
        markMessage(context, uri, true)
    }

    private fun markMessage(context: Context, uri: Uri, error: Boolean) {
        val message = SmsMmsUtils.getSmsMessage(context, uri, null)

        if (message != null && message.moveToFirst()) {
            var body = message.getString(message.getColumnIndex(Telephony.Sms.BODY))
            message.close()

            if (Settings.signature != null && !Settings.signature!!.isEmpty()) {
                body = body.replace("\n" + Settings.signature!!, "")
            }

            val source = DataSource
            val messages = source.searchMessages(context, body)

            if (messages != null && messages.moveToFirst()) {
                val id = messages.getLong(0)
                val conversationId = messages
                        .getLong(messages.getColumnIndex(Message.COLUMN_CONVERSATION_ID))
                val data = messages.getString(messages.getColumnIndex(Message.COLUMN_DATA))

                markMessage(source, context, error, id, conversationId, data)
            } else {
                // if the message was unicode, then it won't match here and would never get marked as sent or error
                val messageList = source.getNumberOfMessages(context, 10)
                var markedAsSent = false
                for (m in messageList) {
                    if (StripAccents.stripAccents(m.data) == body && m.type == Message.TYPE_SENDING) {
                        markMessage(source, context, error, m.id, m.conversationId, m.data)
                        markedAsSent = true
                        break
                    }
                }

                if (!markedAsSent) {
                    val conversationIds = HashSet<Long>()

                    for (m in messageList) {
                        if (m.type == Message.TYPE_SENDING) {
                            source.updateMessageType(context, m.id, if (error) Message.TYPE_ERROR else Message.TYPE_SENT)
                            conversationIds.add(m.conversationId)
                        }
                    }

                    for (id in conversationIds) {
                        MessageListUpdatedReceiver.sendBroadcast(context, id)
                    }
                }
            }

            messages?.closeSilent()
        } else {
            throw RuntimeException("no messages found")
        }
    }

    private fun markMessage(source: DataSource, context: Context, error: Boolean, messageId: Long, conversationId: Long, data: String?) {
        source.updateMessageType(context, messageId, if (error) Message.TYPE_ERROR else Message.TYPE_SENT)

        MessageListUpdatedReceiver.sendBroadcast(context, conversationId)

        val resend = Intent(context, ResendFailedMessage::class.java)
        resend.putExtra(ResendFailedMessage.EXTRA_MESSAGE_ID, messageId)
        resend.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        if (error) {
            if (retryFailedMessages()) {
                context.startService(resend)
            } else {
                val open = ActivityUtils.buildForComponent(ActivityUtils.MESSENGER_ACTIVITY)
                open.putExtra(MessengerActivityExtras.EXTRA_CONVERSATION_ID, conversationId)
                open.putExtra(MessengerActivityExtras.EXTRA_FROM_NOTIFICATION, true)
                open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val pendingOpen = PendingIntent.getActivity(context,
                        conversationId.toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT)

                val notification = NotificationCompat.Builder(context, NotificationUtils.FAILED_MESSAGES_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setContentTitle(context.getString(R.string.message_sending_failed))
                        .setContentText(data)
                        .setColor(ColorSet.DEFAULT(context).color)
                        .setAutoCancel(true)
                        .setContentIntent(pendingOpen)

                val resendPending = PendingIntent.getService(context,
                        messageId.toInt(), resend, PendingIntent.FLAG_UPDATE_CURRENT)
                val action = NotificationCompat.Action(
                        R.drawable.ic_reply_dark, context.getString(R.string.resend), resendPending)

                notification.addAction(action)
                NotificationManagerCompat.from(context)
                        .notify(6666 + messageId.toInt(), notification.build())
            }
        }
    }

    private fun fallbackToLatestMessages(context: Context) {
        try {
            val messageList = DataSource.getNumberOfMessages(context, 10)

            val conversationIds = HashSet<Long>()

            for (m in messageList) {
                if (m.type == Message.TYPE_SENDING) {
                    DataSource.updateMessageType(context, m.id, Message.TYPE_SENT)
                    conversationIds.add(m.conversationId)
                }
            }

            for (id in conversationIds) {
                MessageListUpdatedReceiver.sendBroadcast(context, id)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}
