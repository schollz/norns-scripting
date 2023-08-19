// Engine_CopyPasta
// to use, Ctl+F CopyPasta and replace with your name
Engine_CopyPasta : CroneEngine {

	// CopyPasta specific v0.1.0
	// OPINION: store everything in dictionaries
	//          because its easy to release
	// ALTERNATIVE: use specific variables
	var buses;
	var syns;
	var bufs; 
	var oscs;
	var params;
	// CopyPasta ^

	*new { arg context, doneCallback;
		^super.new(context, doneCallback);
	}

	// off and on are general functions
	// to play and free synthdefs
	off {
		arg synthdef, id;

		// free any synth if it exists
		if (syns.at(synthdef).at(id).notNil,{
			if (syns.at(synthdef).at(id).isRunning,{
				// this will free synth
				syns.at(synthdef).at(id).set(\gate,0);
			});
		});
	}

	on {
		arg synthdef, id, args;
		
		// add the buses to the args
		args=args++[
			busMain: buses.at("busMain"),
			busDelay: buses.at("busDelay"),
			busReverb: buses.at("busReverb"),
		];

		// args with everything in memory
		params.at(key).keysValuesDo({ arg k, v;
			args=args++[k,v];
		});

		// free any synth if it exists
		off.(synthdef,id);

		// create the synth before the main
		syns.at(key).put(id,Synth.before(syns.at("main"),synthdef,args).onFree({
			["[CopyPasta] stopped playing synth",key,id].postln;
		}));

		// watch the new one
		NodeWatcher.register(syns.at(synthdef).at(id));
	}

	alloc {
		// CopyPasta specific v0.0.1
		var s=context.server;

		// initialize the synthdefs 

		// nice kick drum I use a lot
		SynthDef("kick", { |note=20, ratio = 6, sweeptime = 0.05, predb = 0, db = 0,
			decay1 = 0.3, decay1L = 0.8, decay2 = 0.15, clicky=0.0, 
			busMain, busDelay, busReverb,sendDelay=0, sendReverb = 0|
			var snd;
			var basefreq = note.midicps;
			var fcurve = EnvGen.kr(Env([basefreq * ratio, basefreq], [sweeptime], \exp)),
			env = EnvGen.kr(Env([clicky,1, decay1L, 0], [0.0,decay1, decay2], -4), doneAction: Done.freeSelf),
			sig = SinOsc.ar(fcurve, 0.5pi, predb.dbamp).distort * env ;
			snd = (sig*db.dbamp).tanh!2;
			Out.ar(busMain,(1-sendDelay)*(1-sendReverb)*snd);
			Out.ar(busDelay,sendDelay*snd);
			Out.ar(busReverb,sendReverb*snd);
		}).send(s);

		// my favorite synth sound
		SynthDef("synth",{ | amp=0.75,note=40, mix=1.0, detune = 0.4,lpf=10,gate=1,
			busMain, busDelay, busReverb,sendDelay=0, sendReverb = 0|
			var snd;
			var freq=note.midicps;
			var detuneCurve = { |x|
				(10028.7312891634*x.pow(11)) -
				(50818.8652045924*x.pow(10)) +
				(111363.4808729368*x.pow(9)) -
				(138150.6761080548*x.pow(8)) +
				(106649.6679158292*x.pow(7)) -
				(53046.9642751875*x.pow(6)) +
				(17019.9518580080*x.pow(5)) -
				(3425.0836591318*x.pow(4)) +
				(404.2703938388*x.pow(3)) -
				(24.1878824391*x.pow(2)) +
				(0.6717417634*x) +
				0.0030115596
			};
			var centerGain = { |x| (-0.55366 * x) + 0.99785 };
			var sideGain = { |x| (-0.73764 * x.pow(2)) + (1.2841 * x) + 0.044372 };

			var center = Mix.new(SawDPW.ar(freq, Rand()));
			var detuneFactor = freq * detuneCurve.(LFNoise2.kr(1).range(0.3,0.5));
			var freqs = [
				(freq - (detuneFactor * 0.11002313)),
				(freq - (detuneFactor * 0.06288439)),
				(freq - (detuneFactor * 0.01952356)),
				// (freq + (detuneFactor * 0)),
				(freq + (detuneFactor * 0.01991221)),
				(freq + (detuneFactor * 0.06216538)),
				(freq + (detuneFactor * 0.10745242))
			];
			var side = Mix.fill(6, { |n|
				SawDPW.ar(freqs[n], Rand(0, 2))
			});

			var sig =  (center * centerGain.(mix)) + (side * sideGain.(mix));
			sig = HPF.ar(sig ! 2, freq);
			sig = BLowPass.ar(sig,freq*LFNoise2.kr(1).range(4,20),1/0.707);
			sig = Pan2.ar(sig);
			snd = sig * amp * 0.8;
			snd = snd * EnvGen.ar(Env.adsr(0.1,1,0.9,3),gate,doneAction:2);
			Out.ar(busMain,(1-sendDelay)*(1-sendReverb)*snd);
			Out.ar(busDelay,sendDelay*snd);
			Out.ar(busReverb,sendReverb*snd);
		}).send(s);

		// OPINION: use main synthdef with main effects
		SynthDef("main", { |busMain, busDelay, busReverb|
			var sndMain,sndDelay,sndReverb;
			sndMain = In.ar(busMain,2);
			sndDelay = CombC.ar(In.ar(busDelay,2),0.1,0.1,1);
			sndReverb = In.ar(busReverb,2);
			sndReverb = Fverb.ar(sndReverb[0],sndReverb[1]);
			Out.ar(0,sndMain+sndDelay+sndReverb);
		}).send(s);

		 // initialize all the dictionaries
		buses = Dictionary.new();
		bufs = Dictionary.new();
		oscs = Dictionary.new();
		syns = Dictionary.new(); // dictionary of dictionaries
		params = Dictionary.new(); // dictionary of dictionaries

		// OPINION: oscs capture the output from SendReply in the
		// synthdefs and send it back to the norns script.
		// ALTERNATIVE: use polling (see habitus scripts)
		oscs.put("position",OSCFunc({ |msg| NetAddr("127.0.0.1", 10111).sendMsg("progress",msg[3],msg[3]); }, '/position'));

		// create the two buses for the sends (using the dictionary)
		buses.put("busMain",Bus.audio(s,2));
		buses.put("busDelay",Bus.audio(s,2));
		buses.put("busReverb",Bus.audio(s,2));

		// OPINION: keep parameters/synth for each entity in a dictionary of dictionaries
		//          here I have a "synth" and "kick" that need parameters, synths
		params.put("kick",Dictionary.new());
		params.put("synth",Dictionary.new());
		syns.put("kick",Dictionary.new());
		syns.put("synth",Dictionary.new());

		// sync up the server
		s.sync;

		// create the main output synth
		syns.put("main",Synth.new("main",[
			busMain: buses.at("busMain"),
			busDelay: buses.at("busDelay"),
			busReverb: buses.at("busReverb"),
		]));
		NodeWatcher.register(syns.at("main"));


		// OPINION: use one "set" function for everything
		//			with everything stored in dictionaries.
		//			all running synths get updated immediately.
		// 			this function works for both kick + synth.
		this.addCommand("set","ssf",{ arg msg;
			var id=msg[1]; // "synth" or "kick"
			var k=msg[2]; 
			var v=msg[3];

			// update the parameters
			params.at(id).put(k,v);

			// update all running synths
			syns.at(id).keysValuesDo({ arg name, syn;
				if (syn.isRunning,{
					["[CopyPasta] setting syn",id,name,k,"=",v].postln;
					syn.set(k,v);
				});
			});
		});


		this.addCommand("synth_off","f", { arg msg;
			var synthdef="synth";
			var note=msg[1];

			// create id for synth for freeing purposes
			var id="synth"++note.round.asInteger;

			off.(synthdef,id);
		});

		// OPINION: its nice to have specific commands for different instruments.
		this.addCommand("synth_on","ff", { arg msg;
			var synthdef="synth"; // this simply makes it easy to copy the code
			var note=msg[1];
			var velocity=msg[2];

			// create id for synth for freeing purposes
			var id=synthdef++note.round.asInteger;

			var args = [
				note: note,
				velocity: velocity;
			];

			on.(synthdef,id,args);
		});

		this.addCommand("kick_on","", { arg msg;
			var synthdef="kick";
			var id=synthdef;

			on.(synthdef,id,[]);
		});

	}

	free {
		// CopyPasta Specific v0.0.1
		// clear buffers
		bufs.keysValuesDo({ arg buf, val;
			val.free;
		});
		// clear synths (dictionary of dictionaries)
		syns.keysValuesDo({ arg name, synDict;
			synDict.keysValuesDo({ arg k,v;
				v.free;
			});
		});
		// clear buses
		buses.keysValuesDo({ arg buf, val;
			val.free;
		});
		// clear oscs
		oscs.keysValuesDo({ arg buf, val;
			val.free;
		});
		// ^ CopyPasta specific
	}
}
