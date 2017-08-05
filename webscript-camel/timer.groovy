import java.time.LocalDateTime
import static java.time.temporal.ChronoField.*

def now = LocalDateTime.now()

def everySecond = { Closure task -> task() }
def everyMinute = { Closure task -> if (now.get(SECOND_OF_MINUTE) == 0) task() }
def everyHour = { Closure task -> if (now.get(SECOND_OF_MINUTE) == 0 && now.get(MINUTE_OF_HOUR) == 0) task() }

def alert = { title, message -> invoke('script://alert-me', [title, message]) }

everyHour { alert("The time is now", now.toString()) }