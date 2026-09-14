package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NlpEngineTest {

    @Test
    fun `test alarm parsing English and Hindi`() {
        val parsed1 = NlpEngine.parse("Set an alarm for 7 AM")
        assertEquals(IntentType.ALARM_CREATE, parsed1.intent)
        assertEquals(7, parsed1.hour)
        assertEquals(0, parsed1.minute)

        val parsed2 = NlpEngine.parse("Set an alarm for 6:30 every weekday")
        assertEquals(IntentType.ALARM_CREATE, parsed2.intent)
        assertEquals(6, parsed2.hour)
        assertEquals(30, parsed2.minute)
        assertEquals("WEEKDAY", parsed2.repeatPattern)

        val parsedHindi = NlpEngine.parse("Kal subah 7 baje ka alarm laga do")
        assertEquals(IntentType.ALARM_CREATE, parsedHindi.intent)
        assertEquals(7, parsedHindi.hour)
        assertEquals(0, parsedHindi.minute)

        val cancelAlarm = NlpEngine.parse("Cancel my 7 AM alarm")
        assertEquals(IntentType.ALARM_DELETE, cancelAlarm.intent)
        assertEquals(7, cancelAlarm.hour)
    }

    @Test
    fun `test timer parsing English and Hindi`() {
        val timer1 = NlpEngine.parse("Set a timer for 10 minutes")
        assertEquals(IntentType.TIMER_CREATE, timer1.intent)
        assertEquals(600L, timer1.durationSeconds)

        val timer2 = NlpEngine.parse("Start a 25 minute timer")
        assertEquals(IntentType.TIMER_CREATE, timer2.intent)
        assertEquals(1500L, timer2.durationSeconds)

        val timerHindi = NlpEngine.parse("20 minute ka timer laga do")
        assertEquals(IntentType.TIMER_CREATE, timerHindi.intent)
        assertEquals(1200L, timerHindi.durationSeconds)

        val cancelTimer = NlpEngine.parse("Cancel the timer")
        assertEquals(IntentType.TIMER_CANCEL, cancelTimer.intent)
    }

    @Test
    fun `test reminder parsing`() {
        val reminder = NlpEngine.parse("Remind me to study at 8 PM")
        assertEquals(IntentType.REMINDER_CREATE, reminder.intent)
        assertEquals(20, reminder.hour)
        assertEquals(0, reminder.minute)
        assertTrue(reminder.title?.contains("Study", ignoreCase = true) == true)

        val deleteReminder = NlpEngine.parse("Delete my study reminder")
        assertEquals(IntentType.REMINDER_DELETE, deleteReminder.intent)
    }

    @Test
    fun `test calendar parsing`() {
        val meeting = NlpEngine.parse("Create a meeting tomorrow at 3 PM")
        assertEquals(IntentType.CALENDAR_CREATE, meeting.intent)
        assertEquals(15, meeting.hour)
        assertEquals(0, meeting.minute)
        assertEquals("tomorrow", meeting.dateCategory)

        val todayCalendar = NlpEngine.parse("What's on my calendar today?")
        assertEquals(IntentType.CALENDAR_TODAY, todayCalendar.intent)
    }

    @Test
    fun `test call parsing`() {
        val callPerson = NlpEngine.parse("Call Rahul")
        assertEquals(IntentType.CALL_CONTACT, callPerson.intent)
        assertEquals("Rahul", callPerson.contactName)

        val callNumber = NlpEngine.parse("Call 9876543210")
        assertEquals(IntentType.CALL_NUMBER, callNumber.intent)
        assertEquals("9876543210", callNumber.phoneNumber)

        // Hindi and Hinglish variations
        val hindiCall1 = NlpEngine.parse("Rahul ko call karo")
        assertEquals(IntentType.CALL_CONTACT, hindiCall1.intent)
        assertEquals("Rahul", hindiCall1.contactName)

        val hindiCall2 = NlpEngine.parse("Mummy ko call lagao")
        assertEquals(IntentType.CALL_CONTACT, hindiCall2.intent)
        assertEquals("Mummy", hindiCall2.contactName)

        val hindiCall3 = NlpEngine.parse("Call lagao Priya ko")
        assertEquals(IntentType.CALL_CONTACT, hindiCall3.intent)
        assertEquals("Priya", hindiCall3.contactName)

        val hindiCall4 = NlpEngine.parse("Phone lagao Papa ko")
        assertEquals(IntentType.CALL_CONTACT, hindiCall4.intent)
        assertEquals("Papa", hindiCall4.contactName)

        val hindiCallNumber = NlpEngine.parse("9876543210 par call karo")
        assertEquals(IntentType.CALL_NUMBER, hindiCallNumber.intent)
        assertEquals("9876543210", hindiCallNumber.phoneNumber)

        // Show contacts commands
        val showContacts1 = NlpEngine.parse("Show my contacts")
        assertEquals(IntentType.CONTACTS_LIST, showContacts1.intent)

        val showContacts2 = NlpEngine.parse("Contacts list dikhao")
        assertEquals(IntentType.CONTACTS_LIST, showContacts2.intent)

        // Bare "call" missing slot
        val bareCall = NlpEngine.parse("Call")
        assertEquals(IntentType.CALL_CONTACT, bareCall.intent)
        assertTrue(bareCall.missingSlots.contains("contactName"))

        val bareCallHindi = NlpEngine.parse("Call karo")
        assertEquals(IntentType.CALL_CONTACT, bareCallHindi.intent)
        assertTrue(bareCallHindi.missingSlots.contains("contactName"))
    }

    @Test
    fun `test call slot follow up in ConversationManager`() {
        val manager = ConversationManager()
        val partial = NlpEngine.parse("Call someone")
        manager.setPendingAction(
            PendingAction(
                intent = partial.intent,
                partialIntent = partial,
                awaitingSlot = "contactName"
            )
        )

        // When user replies with a contact name
        val followUpName = manager.processFollowUp("Aarav")
        assertNotNull(followUpName.first)
        assertEquals(IntentType.CALL_CONTACT, followUpName.first?.intent)
        assertEquals("Aarav", followUpName.first?.contactName)

        // When user replies with a phone number
        manager.setPendingAction(
            PendingAction(
                intent = partial.intent,
                partialIntent = partial,
                awaitingSlot = "contactName"
            )
        )
        val followUpNumber = manager.processFollowUp("+91 9876543210")
        assertNotNull(followUpNumber.first)
        assertEquals(IntentType.CALL_NUMBER, followUpNumber.first?.intent)
        assertEquals("+919876543210", followUpNumber.first?.phoneNumber)
    }

    @Test
    fun `test conversational manager follow up for missing slot`() {
        val manager = ConversationManager()
        val partial = NlpEngine.parse("Remind me tomorrow")
        assertEquals(IntentType.REMINDER_CREATE, partial.intent)
        assertTrue(partial.missingSlots.contains("task"))

        manager.setPendingAction(
            PendingAction(
                intent = partial.intent,
                partialIntent = partial,
                awaitingSlot = "task"
            )
        )

        val followUp = manager.processFollowUp("submit project at 8 PM")
        assertNotNull(followUp.first)
        assertEquals(20, followUp.first?.hour)
        assertTrue(followUp.first?.title?.contains("Submit project", ignoreCase = true) == true)
    }
}
