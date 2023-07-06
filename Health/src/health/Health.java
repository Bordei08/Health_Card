/** 
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
 * 
 */

package health;

import javacard.framework.*;
import javacardx.annotations.*;
//import static health.HealthStrings.*;

/**
 * Applet class
 * 
 * @author <user>
 */
@StringPool(value = { @StringDef(name = "Package", value = "health"),
		@StringDef(name = "AppletName", value = "Health") },
		// Insert your strings here
		name = "HealthStrings")

public class Health extends Applet {

	/* constants declaration */

	// code of CLA byte in the command APDU header
	final static byte Health_CLA = (byte) 0x80;
	final static byte VERIFY = (byte) 0x20;
	final static byte CHANGE_PIN = (byte) 0x30;
	final static byte GET_INFORMATIONS = (byte) 0x40;
	final static byte SET_INFORMATIONS = (byte) 0x50;
	final static byte SET_CONSULT_DATA = (byte) 0x70;
	final static byte SET_MEDICAL_VACATION = (byte) 0x90;

	// maximum number of incorrect tries before the
	// PIN is blocked
	final static byte PIN_TRY_LIMIT = (byte) 0x03;
	// maximum size PIN
	final static byte MAX_PIN_SIZE = (byte) 0x08;
	// signal that the PIN verification failed
	final static short SW_VERIFICATION_FAILED = 0x6300;
	//need to check the pin
	final static short SW_PIN_VERIFICATION_REQUIRED = (short) 0x6301;
	// No information given (Bytes P1 and/or P2 are incorrect)
	final static short SW_WRONG_PARAMTER = 0x6a00;
	// There is insufficient memory space in record or file
	final static short SW_INVALID_VALUE = 0x6a84;
	// Not have access to this option
	final static short SW_ACCESS_DENIED = (short) 0x6982;
	
	OwnerPIN pin;
	byte[] date_of_birth ; // 3 bytes
	byte blood_group_code; // 1 byte
	byte RH_code; // 1 byte
	byte chronic_diagnosis_code; // 1 byte
	byte chronic_specialty_code; // 1 byte
	byte donor_code; // 1 byte

	public class Consultation {
		public byte diagnostic_code;// 1 byte
		public byte specialty_code;// 1 byte
		public byte[] date_diagnostic;// 3 byte
	};

	Consultation[] cons = new Consultation[3];
	byte[] begin_medical_vacation; 
	byte[] end_medical_vacation;

	private Health(byte[] bArray, short bOffset, byte bLength) {

		// It is good programming practice to allocate
		// all the memory that an applet needs during
		// its lifetime inside the constructor
		pin = new OwnerPIN(PIN_TRY_LIMIT, MAX_PIN_SIZE);

		byte iLen = bArray[bOffset]; // aid length
		bOffset = (short) (bOffset + iLen + 1);
		byte cLen = bArray[bOffset]; // info length
		bOffset = (short) (bOffset + cLen + 1);
		byte aLen = bArray[bOffset]; // applet data length

		// The installation parameters contain the PIN
		// initialization value
		pin.update(bArray, (short) (bOffset + 1), aLen);
		
		
		date_of_birth = new byte[3];
		
		date_of_birth[0] = (byte) 0x08;
		date_of_birth[1] = (byte) 0x0b;
		date_of_birth[2] = (byte) 0x01;
		
		blood_group_code = (byte) 0x00;
		
		RH_code = (byte) 0x01;
		Consultation  consultation = new Consultation(); 
		
		
		consultation.date_diagnostic = new byte[3];
		consultation.date_diagnostic[0] = (byte) 0x01;
		consultation.date_diagnostic[1] = (byte) 0x02;
		consultation.date_diagnostic[2] = (byte) 0x16;
		
		consultation.diagnostic_code = (byte) 0x01;
		
		consultation.specialty_code = (byte) 0x01;
		
		cons = new Consultation[]{consultation};
		
		begin_medical_vacation  = new byte[3];
		end_medical_vacation  = new byte[3];
		
		begin_medical_vacation[0] = (byte) 0x02;
		begin_medical_vacation[1] = (byte) 0x02;
		begin_medical_vacation[2] = (byte) 0x17;
		
		end_medical_vacation[0] = (byte) 0x08;
		end_medical_vacation[1] = (byte) 0x02;
		end_medical_vacation[2] = (byte) 0x17;
		
		register();

	} // end of the constructor

	/**
	 * Installs this applet.
	 * 
	 * @param bArray  the array containing installation parameters
	 * @param bOffset the starting offset in bArray
	 * @param bLength the length in bytes of the parameter data in bArray
	 */
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// create a Health applet instance
		new Health(bArray, bOffset, bLength);
	} // end of install method

	@Override
	public boolean select() {
		// The applet declines to be selected if the pin is blocked.
		if (pin.getTriesRemaining() == 0) {
			return false;
		}
		return true;
	}// end of select method

	@Override
	public void deselect() {
		// reset the pin value
		pin.reset();
	}

	/**
	 * Processes an incoming APDU.
	 * 
	 * @see APDU
	 * @param apdu the incoming APDU
	 */
	@Override
	public void process(APDU apdu) {
		// Insert your code here

		byte[] buffer = apdu.getBuffer();

		if (apdu.isISOInterindustryCLA()) {
			if (buffer[ISO7816.OFFSET_INS] == (byte) (0xA4)) {
				return;
			}
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}

		if (buffer[ISO7816.OFFSET_CLA] != Health_CLA) {
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		}

		switch (buffer[ISO7816.OFFSET_INS]) {
		case VERIFY:
			verify(apdu);
			return;
		case CHANGE_PIN:
			change_pin(apdu);
			return;
		case GET_INFORMATIONS:
			get_informations(apdu);
			return;
		case SET_INFORMATIONS:
			aet_informations(apdu);
			return;	
		case SET_CONSULT_DATA:
			set_consult_data(apdu);
			return;
		case SET_MEDICAL_VACATION:
			set_medical_vacation(apdu);
			return;
		default:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}

	} // end of process method

	private void verify(APDU apdu) {

		byte[] buffer = apdu.getBuffer();
		// retrieve the PIN data for validation.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());

		// check pin
		// the PIN data is read into the APDU buffer
		// at the offset ISO7816.OFFSET_CDATA
		// the PIN data length = byteRead
		if (pin.check(buffer, ISO7816.OFFSET_CDATA, byteRead) == false) {
			ISOException.throwIt(SW_VERIFICATION_FAILED);
		}
	} // end of verify method

	public void change_pin(APDU apdu) {
		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		byte[] buffer = apdu.getBuffer();
		// retrieve the PIN data for change.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());

		byte numBytes = (buffer[ISO7816.OFFSET_LC]);
		//I check if it meets the requirements of a pin
		if ((numBytes > MAX_PIN_SIZE) || (byteRead > MAX_PIN_SIZE) || numBytes < 1 || byteRead < 1) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}
		// update pin
		pin.update(buffer, ISO7816.OFFSET_CDATA, byteRead);

	}// end of change_pin method

	public void get_informations(APDU apdu) {
		byte[] buffer = apdu.getBuffer();

		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		short le = apdu.setOutgoing();
		// maximum answer of 30
		if (le < 30) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		} else {
			// informs the CAD the actual number of bytes
			// returned
			apdu.setOutgoingLength((byte) 30);

			// date of birth 3 bytes
			buffer[0] = (byte) (date_of_birth[0]);
			buffer[1] = (byte) (date_of_birth[1]);
			buffer[2] = (byte) (date_of_birth[2]);
		
			// blood group code 1 byte
			buffer[3] = (byte) (blood_group_code);

			// rh 1 byte
			buffer[4] = (byte) (RH_code);
			
			// chronic diagnosis 1 byte
			buffer[5] = (byte) (chronic_diagnosis_code);
		
			// chronic specialty 1 byte
			buffer[6] = (byte) (chronic_specialty_code);
			
			//  donor 1 byte
			buffer[7] = (byte) (donor_code);
			
			//consultations max 3 => 3 * 5 = 15 bytes
			buffer[8] = (byte) (cons.length);
			
			short index = 8;
			for(short i = 0; i < (short) cons.length; i ++) {
				index = (short)(index + 1);
				// diagnostic 1  byte
				buffer[index] = (byte) (cons[i].diagnostic_code);
				index = (short)(index + 1);
				// specialty 1 byte
				buffer[index] = (byte) (cons[i].specialty_code);
				index = (short)(index + 1);
				// date
				// day 1 byte
				buffer[index] = (byte) (cons[i].date_diagnostic[0] );
				index = (short)(index + 1);
				// month 1 byte
				buffer[index] = (byte) (cons[i].date_diagnostic[1] );
				index = (short)(index + 1);
				// year 1 byte
				buffer[index] = (byte) (cons[i].date_diagnostic[2] );
			}
			
			// begin medical vacation
			// day 1 byte
			index = (short)(index + 1);
			buffer[index] = (byte) (begin_medical_vacation[0]);
			//month 1 byte
			index = (short)(index + 1);
			buffer[index] = (byte) (begin_medical_vacation[1]);
			//year 1 byte
			index = (short)(index + 1);
			buffer[index] = (byte) (begin_medical_vacation[2]);
			
			// end medical vacation
			// day 1 byte
			index = (short)(index + 1);
			buffer[index] = (byte) (end_medical_vacation[0]);
			// month 1 byte
			index = (short)(index + 1);
			buffer[index] = (byte) (end_medical_vacation[1]);
			//year 1byte
			index = (short)(index + 1);
			buffer[index] = (byte) (end_medical_vacation[2]);
			index = (short)(index + 1);
			// send answer
			apdu.sendBytes((short) 0, index);

		}
	}// end of get_informations method

	public void aet_informations(APDU apdu) {
		byte[] buffer = apdu.getBuffer();

		if (!pin.isValidated()) {
			ISOException.throwIt(SW_PIN_VERIFICATION_REQUIRED);
		}
		//parameters p1 and p2 will help me see what I have to set
		// read P1 and P2
		byte p1 = buffer[ISO7816.OFFSET_P1];
		byte p2 = buffer[ISO7816.OFFSET_P2];
		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		byte lc = buffer[ISO7816.OFFSET_LC];

		// indicate that this APDU has incoming data
		// and receive data starting from the offset
		// ISO7816.OFFSET_CDATA following the 5 header
		// bytes.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());
		if (lc < 1 || byteRead < 1) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

		}
		if (p1 == (byte) 0x01 && p2 == (byte) 0x01) {
			// get the new chronic_diagnosis_code
			short new_chronic_diagnosis_code = (short) (buffer[ISO7816.OFFSET_CDATA] & 0xFF);
			// check the new value
			if (new_chronic_diagnosis_code < 0 || new_chronic_diagnosis_code > 255) {
				ISOException.throwIt(SW_INVALID_VALUE);
			}
			// update
			chronic_diagnosis_code = (byte) new_chronic_diagnosis_code;
		} else if (p1 == (byte) 0x01 && p2 == (byte) 0x00) {
			// get the new chronic_specialty_code
			short new_chronic_specialty_code = (short) (buffer[ISO7816.OFFSET_CDATA] & 0xFF);
			// check the new value
			if (new_chronic_specialty_code < 0 || new_chronic_specialty_code > 255) {
				ISOException.throwIt(SW_INVALID_VALUE);
			}
			// update
			chronic_specialty_code = (byte) new_chronic_specialty_code;
		} else if (p1 == (byte) 0x00 && p2 == (byte) 0x01) {
			// get the new donor_code
			short new_donor_code = (short) (buffer[ISO7816.OFFSET_CDATA] & 0xFF);
			//check the new value
			if (new_donor_code < 0 || new_donor_code > 1) {
				ISOException.throwIt(SW_INVALID_VALUE);
			}
			// update
			donor_code = (byte) new_donor_code;
		} else {
			// P1 = 0 AND P2 = 0 =>
			ISOException.throwIt(SW_WRONG_PARAMTER);
		}
	}// end of set_informations method

	public void set_consult_data(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		byte lc = buffer[ISO7816.OFFSET_LC];
		// indicate that this APDU has incoming data
		// and receive data starting from the offset
		// ISO7816.OFFSET_CDATA following the 5 header
		// bytes.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());
		if (lc < 1 || byteRead < 1 || lc > 4 || byteRead > 4) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}
		// I take the current date and specialization
		byte[] current_date = new byte[3];

		for (short i = 0; i < lc - 1; i++) {
			current_date[i] = buffer[(byte) (ISO7816.OFFSET_CDATA + i)];
		}

		byte spec = buffer[(byte) (ISO7816.OFFSET_CDATA + (lc - 1))];
		// I check if the required specialization was not checked 
		//in the most recent month from the list of consultees 
		//and at the same time I save the index for the oldest consultation
		byte last_year = (byte) 0x0;
		byte last_month = (byte) 0x0;
		byte last_day = (byte) 0x00;
		byte last_spec = (byte) 0x0;

		byte first_year = (byte) 0x63;
		byte first_month = (byte) 0xc;
		byte first_day = (byte) 0x1f;
		short index_first_cons = (short) 0;

		for (short i = 0; i < cons.length; i++) {
			if (cons[i].date_diagnostic[1] >= last_month && cons[i].date_diagnostic[2] >= last_year
					&& cons[i].date_diagnostic[0] >= last_day) {
				last_month = cons[i].date_diagnostic[1];
				last_year = cons[i].date_diagnostic[2];
				last_day = cons[i].date_diagnostic[0];
				if(cons[i].specialty_code == spec)
					last_spec = cons[i].specialty_code;
			}
			if (cons[i].date_diagnostic[1] <= first_month && cons[i].date_diagnostic[2] <= first_year
					&& cons[i].date_diagnostic[0] <= first_day) {
				last_month = cons[i].date_diagnostic[1];
				last_year = cons[i].date_diagnostic[2];
				first_day = cons[i].date_diagnostic[0];
				index_first_cons = i;
			}
		}
		//I treat the two cases, a healthy patient
		//and a chronic patient, for each case 
		//I check the specific condition 
		//and how the new consultation should be memorized 
		//(if applicable)
		if (chronic_diagnosis_code == (byte) 0x00) {

			if (spec == last_spec && current_date[1] == last_month) {
				ISOException.throwIt(SW_ACCESS_DENIED);
			} else {
				if (cons.length < 3) {
					Consultation newConsultation = new Consultation();
					newConsultation.diagnostic_code =  chronic_diagnosis_code; // diagnostic_code
					newConsultation.specialty_code = (byte) spec; // specialty_code
					newConsultation.date_diagnostic = new byte[] { current_date[0], current_date[1], current_date[2] };

					Consultation[] newCons = new Consultation[(short) (cons.length + 1)];

					for (short i = 0; i < (short) cons.length; i++) {
						newCons[i] = cons[i];
					}
					newCons[cons.length] = newConsultation;
					cons = newCons;

				} else {
					cons[index_first_cons].diagnostic_code = (byte) chronic_diagnosis_code;
					cons[index_first_cons].specialty_code = spec;
					cons[index_first_cons].date_diagnostic[0] = current_date[0];
					cons[index_first_cons].date_diagnostic[1] = current_date[1];
					cons[index_first_cons].date_diagnostic[2] = current_date[2];
				}
			}
		} else {
			if (spec != chronic_specialty_code) {
				ISOException.throwIt(SW_ACCESS_DENIED);
			} 
			else if (chronic_diagnosis_code > (byte) 0x00) {
				if (cons.length < 3) {
					Consultation newConsultation = new Consultation();
					newConsultation.diagnostic_code = chronic_diagnosis_code ; // diagnostic_code
					newConsultation.specialty_code = (byte) spec; // specialty_code
					newConsultation.date_diagnostic = new byte[] { current_date[0], current_date[1], current_date[2] };

					Consultation[] newCons = new Consultation[(short) (cons.length + 1)];

					for (short i = 0; i < (short) cons.length; i++) {
						newCons[i] = cons[i];
					}
					newCons[cons.length] = newConsultation;
					cons = newCons;

				} else {
					cons[index_first_cons].diagnostic_code = (byte) chronic_diagnosis_code;
					cons[index_first_cons].specialty_code = spec;
					cons[index_first_cons].date_diagnostic[0] = current_date[0];
					cons[index_first_cons].date_diagnostic[1] = current_date[1];
					cons[index_first_cons].date_diagnostic[2] = current_date[2];
				}
			}

		}
	}// end of set_consult_data method

	public void set_medical_vacation(APDU apdu) {
		byte[] buffer = apdu.getBuffer();
		// inform system that the applet has finished
		// processing the command and the system should
		// now prepare to construct a response APDU
		// which contains data field
		byte lc = buffer[ISO7816.OFFSET_LC];
		// indicate that this APDU has incoming data
		// and receive data starting from the offset
		// ISO7816.OFFSET_CDATA following the 5 header
		// bytes.
		byte byteRead = (byte) (apdu.setIncomingAndReceive());
		if (lc != 4 || byteRead != 4) {
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		}
		//I take the current date and the number of vacation days
		byte[] current_date = new byte[3];

		for (short i = 0; i < lc - 1; i++) {
			current_date[i] = buffer[(byte) (ISO7816.OFFSET_CDATA + i)];
		}

		byte days_number = buffer[(byte) (ISO7816.OFFSET_CDATA + (lc - 1))];
		//For a healthy patient,
		//I check if he has not requested more than 10 days
		//and if he has not already had 10 days of medical vacation in the current month
		// after updating the medical vacation dates
		if(chronic_diagnosis_code == (byte) 0x00) {
			short days = 0;
			if(end_medical_vacation[1] == current_date[1]) {
				if(begin_medical_vacation[1] == end_medical_vacation[1]) {
					days = (short)(end_medical_vacation[0] - begin_medical_vacation[0] + 1);
				}
				else {
					days = (short)(end_medical_vacation[0]);
				}
				if((byte)(days + days_number) > 0xa) {
					ISOException.throwIt(SW_ACCESS_DENIED);
				}
				else {
					update_medical_vacation(current_date, days_number);
				}
				
			}
			else{
				if(days_number > (byte) 0xa) {
					ISOException.throwIt(SW_ACCESS_DENIED);
				}
				else {
					update_medical_vacation(current_date, days_number);
				}
			}
		}
		//for a chronic patient I offer an unlimited number of days
		// after updating the medical vacation dates
		else if(chronic_diagnosis_code != (byte) 0x00){
			update_medical_vacation(current_date,days_number);
		}
		
		
	}// end of set_medical_vacation method
	
	
	public void update_medical_vacation(byte[] date, byte days_number) {
		
		// If an old month has ended,
		//I update the start date of the medical vacation
		if(begin_medical_vacation[1] != date[1])
			begin_medical_vacation[0] = date[0];
			begin_medical_vacation[1] = date[1];
			begin_medical_vacation[2] = date[2];
		
			
			
		//Below I check that the end date of the medical vacation is correct,
		//respecting the calendar rules
			
		byte new_month = date[1];
		byte new_day ;
		byte new_year =date[2];;
		
		new_day = (byte) (date[0] + days_number);
		
		if(new_day > (byte) 0x1c  && new_month == (byte) 0x02 && (short)(new_year % 4) == 0) {
			new_day = (byte)(0x1c - new_day);
			new_month = (byte) 0x03;
		}
		else if(new_day > (byte) 0x1d  && new_month == (byte) 0x02 && (short)(new_year % 4) != 0) {
			new_day = (byte)(0x1d - new_day);
			new_month = (byte) 0x03;
		}
		else if(new_day > (byte) 0x1e && (new_month == (byte) 0x04 || new_month == (byte) 0x06 || new_month == (byte) 0x09 || new_month == (byte) 0x0b)) {
			new_day = (byte)(0x1e - new_day);
			new_month = (byte)(new_month + 0x01);
		}
		else if(new_day > (byte) 0x1f && new_month == (byte) 0x0c) {
			new_day = (byte)(0x1f - new_day);
			new_month = (byte) 0x01;
			new_year = (byte)(new_year + 0x01);
		}
		else if(new_day > (byte) 0x1f) {
			new_day = (byte)(0x1f - new_day);
			new_month = (byte) (0x01 + new_month);
		}
		
		end_medical_vacation[0] = new_day;
		end_medical_vacation[1] = new_month;
		end_medical_vacation[2] = new_year;
		
	}// end of update_medical_vacation method

}
