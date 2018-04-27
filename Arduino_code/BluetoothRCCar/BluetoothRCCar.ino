#include <SoftwareSerial.h>
#include <Servo.h>
SoftwareSerial BTserial(2, 3); // RX | TX

#define TURN_PIN 7
#define MIN_TURN 1100
#define MAX_TURN 2100
Servo turn;
char command;
String string;
int svangle = 0;
int slideBarValue = 50;
int index = 0;
String aCmd;

#define CAR_GO 11
#define CAR_BACK 12

void carGo(int st){
  digitalWrite(CAR_GO, st);
  delay(10);
}

void carBack(int st){
  digitalWrite(CAR_BACK, st);
  delay(10);
}

void setup()
{
  //Serial.begin( 9600 );//115200
  BTserial.begin( 19200 );
  pinMode(LED_BUILTIN, OUTPUT);
  turn.attach(TURN_PIN);
  svangle = map(slideBarValue, 0, 100, MIN_TURN, MAX_TURN);
  turn.write(svangle);
  pinMode(CAR_GO, OUTPUT);
  pinMode(CAR_BACK, OUTPUT);
  carGo(LOW);
  carBack(LOW);
  //Serial.println("Setup done!");
}

void ledOn()
{
  digitalWrite(LED_BUILTIN, HIGH);
  delay(10);
}

void ledOff()
{
  digitalWrite(LED_BUILTIN, LOW);
  delay(10);
}

void writeString(String stringData) { // Used to serially push out a String with Serial.write()
  for (int i = 0; i < stringData.length(); i++)
  {
    BTserial.write(stringData[i]);   // Push each char 1 by 1 on each loop pass
  }
}

void sendAck(String toSend){
   char payload[toSend.length()+1];
   toSend.toCharArray(payload, sizeof(payload));
   BTserial.write((uint8_t *)payload,sizeof(payload));
}

void loop()
{
  string = "";
  while(BTserial.available() > 0)
  {
    command = ((byte)BTserial.read());
    if(command == ':')
    {
      break;
    }
    else
    {
      string += command;
    }
    delay(1);
  }
  //if(string != "")  Serial.println(string);
  
  while( string.length() >= 3 ){
      aCmd = string.substring(0, 3);
      string = string.substring(3);
      //Serial.println(" " + aCmd);

      index = aCmd.lastIndexOf("T");
      if( aCmd == "GOO"  ){
        // Move the car 
        carGo(HIGH);
      } else if( aCmd == "STG" ){
        carGo(LOW);
        // Stop the car
      } else if( aCmd == "BAC" ){
        // Move the car back
        carBack(HIGH);
      } else if( aCmd == "STB" ){
        // Stop the car
        carBack(LOW);
      } else if( index == 0 ){
        // Turn left/right: cmd = "T<value from 0 to 100>"
          slideBarValue = aCmd.substring(index+1).toInt();
          //Serial.println(slideBarValue );
          if( slideBarValue > 0 ){
            //turn.attach(TURN_PIN);
            svangle = map(slideBarValue, 0, 100, MIN_TURN, MAX_TURN);
            turn.write(svangle);
          }
      }
  }
}

