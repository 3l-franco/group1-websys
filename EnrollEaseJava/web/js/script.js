// EnrollEase front end. All data and rules come from the Java server (see src/enrollease/web/Api.java);
// this file only sends requests with $.ajax and draws the answers on the page.
$(function () {
  $.ajaxSetup({ cache: false });

  // Opening the .html file by double-clicking cannot reach the Java server.
  if (window.location.protocol === "file:") {
    $("body").prepend('<div class="alert alert-warning text-center mb-0 rounded-0">This page was opened as a file, so it cannot talk to the Java server. ' +
      'Run <strong>WebApp.java</strong> in Eclipse, then open <strong>http://localhost:8080</strong> in your browser.</div>');
  }

  var esc = function (s) { return $("<div>").text(s == null ? "" : s).html(); };

  // Calls the Java server and always gives back the parsed JSON (even for errors).
  var call = function (method, url, data) {
    var d = $.Deferred();
    $.ajax({ url: url, method: method, data: data, dataType: "json" })
      .done(function (r) { d.resolve(r); })
      .fail(function (x) { d.resolve(x.responseJSON || { ok: false, message: "The server is temporarily unavailable. Please refresh the page and try again." }); });
    return d.promise();
  };

  // Back to top
  $(window).on("scroll", function () { $(this).scrollTop() > 200 ? $("#backToTop").fadeIn(150) : $("#backToTop").fadeOut(150); });
  $("#backToTop").on("click", function () { $("html, body").animate({ scrollTop: 0 }, 400); });

  // Home: carousel auto-slides every 2 seconds
  $("#heroCarousel").each(function () { new bootstrap.Carousel(this, { interval: 5000, ride: "carousel" }); });

  var logout = function (e) {
    e.preventDefault();
    call("POST", "api/logout").then(function () { window.location.href = "login.html"; });
  };
  $("#logoutLink").on("click", logout);

  // Login / Register (these do not need to know who is logged in)
  $("#goRegister").on("click", function (e) { e.preventDefault(); bootstrap.Tab.getOrCreateInstance($("#registerTabBtn")[0]).show(); });
  $("#goLogin").on("click", function (e) { e.preventDefault(); bootstrap.Tab.getOrCreateInstance($("#loginTabBtn")[0]).show(); });
  $("#togglePass").on("click", function () {
    var f = $("#password"), hidden = f.attr("type") === "password";
    f.attr("type", hidden ? "text" : "password"); $(this).text(hidden ? "Hide" : "Show");
  });
  // Remember only the ID/username on this browser; never save the password.
  var rememberedId = "";
  try { rememberedId = localStorage.getItem("enrolleaseRememberId") || ""; } catch (e) { }
  if ($("#loginForm").length && rememberedId) {
    $("#userId").val(rememberedId);
    $("#remember").prop("checked", true);
  }

  $("#loginForm").on("submit", function (e) {
    e.preventDefault();
    $("#userId, #password").removeClass("is-invalid");
    var id = $.trim($("#userId").val()), pass = $("#password").val();
    if (id === "" || pass === "") {
      if (id === "") $("#userId").addClass("is-invalid");
      if (pass === "") $("#password").addClass("is-invalid");
      $("#loginAlert").text("Please fill in all fields.").removeClass("d-none"); return;
    }
    call("POST", "api/login", { id: id, password: pass }).then(function (r) {
      if (!r.ok) { $("#loginAlert").text(r.message).removeClass("d-none"); return; }
      try {
        if ($("#remember").is(":checked")) localStorage.setItem("enrolleaseRememberId", id);
        else localStorage.removeItem("enrolleaseRememberId");
      } catch (e) { }
      window.location.href = r.role === "admin" ? "admin.html" : (r.role === "staff" ? "staff.html" : "enrollment.html");
    });
  });
  var loadRegistrationPrograms = function () {
    if (!$("#requestedProgram").length) return;
    call("GET", "api/programs").then(function (r) {
      var html = '<option value="">Choose your intended program</option>';
      (r.programs || []).forEach(function (program) { html += '<option value="' + esc(program.id) + '">' + esc(program.code + " - " + program.name) + '</option>'; });
      $("#requestedProgram").html(html);
    });
  };
  var toggleRegistrationType = function () {
    var transferee = $("#studentType").val() === "Transferee";
    $("#transfereeFields").toggleClass("d-none", !transferee);
    $("#requestedProgramWrap").toggleClass("d-none", transferee);
    $("#previousSchool, #previousProgram").prop("required", transferee);
    $("#requestedProgram").prop("required", !transferee);
  };
  loadRegistrationPrograms();
  $("#studentType").on("change", toggleRegistrationType);
  toggleRegistrationType();
  $("#registerForm").on("submit", function (e) {
    e.preventDefault();
    var form = this, box = $("#regAlert");
    call("POST", "api/register", {
      name: $("#fullName").val(), email: $("#email").val(), type: $("#studentType").val(), password: $("#regPass").val(),
      programId: $("#requestedProgram").val(), previousSchool: $("#previousSchool").val(), previousProgram: $("#previousProgram").val()
    }).then(function (r) {
      if (r.ok) { box.removeClass("d-none alert-danger").addClass("alert-success").text(r.message); form.reset(); toggleRegistrationType(); }
      else { box.removeClass("d-none alert-success").addClass("alert-danger").text(r.message); }
    });
  });

  // Contact (the server checks every field again)
  $("#contactForm").on("submit", function (e) {
    e.preventDefault();
    var form = this;
    $("#contactAlert, #contactError").addClass("d-none");
    $("#cName, #cEmail, #cTopic, #cMessage, #cAgree").removeClass("is-invalid");
    call("POST", "api/contact", {
      name: $("#cName").val(), email: $("#cEmail").val(), topic: $("#cTopic").val(),
      message: $("#cMessage").val(), agree: $("#cAgree").is(":checked"),
      type: $("#contStudent").is(":checked") ? "Continuing student" : "New student"
    }).then(function (r) {
      if (r.ok) { $("#contactAlert").removeClass("d-none").text(r.message || "This demo validates the message only; it does not save or send messages."); form.reset(); return; }
      $("#contactError").removeClass("d-none").text(r.message || "Please fix the highlighted fields.");
      (r.fields || []).forEach(function (id) { $("#" + id).addClass("is-invalid"); });
    });
  });

  // Services: filter
  var icons = { "Credential Upload": "\uD83D\uDCC4", "Credential Verification": "\u2705", "Schedule Checking": "\uD83D\uDCC5",
                "Prerequisite Locking": "\uD83D\uDD12", "Subject and Section Management": "\uD83D\uDCDA", "Payment Queue Number": "\uD83D\uDD14" };
  var loadServices = function (filter) {
    call("GET", "api/services", { category: filter }).then(function (r) {
      if (!r.services) {
        // Java server not reachable: filter the cards that are already on the page (same as the original site)
        $(".service-item").each(function () {
          $(this).toggle(filter === "all" || $(this).data("category") === filter);
        });
        return;
      }
      var html = "";
      r.services.forEach(function (s) {
        html += '<div class="col-md-6 col-lg-4 service-item" data-category="' + esc(s.category) + '"><div class="service-card"><div class="icon">' + (icons[s.title] || "") + "</div><h3>" + esc(s.title) + "</h3><p>" + esc(s.description) + "</p></div></div>";
      });
      $("#servicesGrid").html(html);
    });
  };
  if ($("#servicesGrid").length) {
    loadServices("all");
    $("#serviceFilters .btn").on("click", function () {
      $("#serviceFilters .btn").removeClass("btn-primary active").addClass("btn-outline-primary");
      $(this).removeClass("btn-outline-primary").addClass("btn-primary active");
      loadServices($(this).data("filter"));
    });
  }

  // Calendar (the grid is built by Java)
  if ($("#calendar").length) {
    var calY = null, calM = null;
    var drawCal = function () {
      call("GET", "api/calendar", calY ? { year: calY, month: calM } : {}).then(function (r) {
        if (!r.grid) {
          $("#calendar tbody").html('<tr><td colspan="7" class="text-danger py-4">' + esc(r.message) + "</td></tr>");
          return;
        }
        calY = r.year; calM = r.month;
        $("#calTitle").text(r.title);
        var html = "";
        r.grid.forEach(function (week) {
          if (week.every(function (d) { return d === 0; })) return;
          html += "<tr>";
          week.forEach(function (d) {
            var c = d === 0 ? "" : (d === r.today ? "today" : (r.events.indexOf(d) > -1 ? "event" : ""));
            html += '<td class="' + c + '">' + (d === 0 ? "" : d) + "</td>";
          });
          html += "</tr>";
        });
        $("#calendar tbody").html(html);
      });
    };
    $("#prevMonth").on("click", function () { calM--; if (calM < 1) { calM = 12; calY--; } drawCal(); });
    $("#nextMonth").on("click", function () { calM++; if (calM > 12) { calM = 1; calY++; } drawCal(); });
    drawCal();
  }

  // ---------- pages that need a logged-in user ----------
  call("GET", "api/me").then(function (me) {
    if (me.loggedIn) {
      var destination = me.role === "admin" ? "admin.html" : (me.role === "staff" ? "staff.html" : "enrollment.html");
      var $pageLink = $('.navbar .navbar-nav a.nav-link[href="enrollment.html"]');
      if ($pageLink.length && me.role !== "student") {
        $pageLink.text(me.role === "admin" ? "Admin Maintenance" : "Staff Workspace").attr("href", destination);
      }
      $('a.nav-link[href="login.html"]').text("Logout (" + me.name + ")").attr("href", "#").on("click", logout);
    }
    if ($("#adminPage").length) {
      if (me.role !== "admin") { window.location.href = me.role === "student" ? "enrollment.html" : (me.role === "staff" ? "staff.html" : "login.html"); return; }
      startAdmin();
    }
    if ($("#staffPage").length) {
      if (me.role !== "staff") { window.location.href = me.role === "admin" ? "admin.html" : (me.role === "student" ? "enrollment.html" : "login.html"); return; }
      startStaff(me);
    }
    if ($("#submitEnroll").length) {
      if (me.role !== "student") { window.location.href = me.role === "admin" ? "admin.html" : (me.role === "staff" ? "staff.html" : "login.html"); return; }
      startEnrollment();
    }
  });

  // =================== ADMIN ===================
  function startAdmin() {
    var DB = { user: [], staff: [] }, mode = "user", editIndex = -1, modal = new bootstrap.Modal($("#formModal")[0]);
    var CFG = {
      user: { label: "User", role: "student", idLabel: "Student ID", extraLabel: "Student type", options: ["New Student", "Continuing Student", "Transferee"], rows: "#userRows", count: "#userCount" },
      staff: { label: "Staff", role: "staff", idLabel: "Username", extraLabel: "Position", options: ["Registrar", "Cashier", "Clerk", "Administrator"], rows: "#staffRows", count: "#staffCount" }
    };
    var flash = function (text, bad) {
      $("#adminMsg").html('<div class="alert alert-' + (bad ? "danger" : "success") + '">' + esc(text) + "</div>");
      setTimeout(function () { $("#adminMsg").empty(); }, 2500);
    };
    var render = function () {
      ["user", "staff"].forEach(function (k) {
        var html = "";
        DB[k].forEach(function (r, i) {
          html += "<tr><td>" + esc(r.id) + "</td><td>" + esc(r.name) + "</td><td>" + esc(r.email) + "</td><td>" + esc(r.extra) +
            '</td><td class="text-nowrap"><button class="btn btn-sm btn-outline-primary" data-edit="' + k + ":" + i + '">Edit</button> <button class="btn btn-sm btn-outline-danger" data-del="' + k + ":" + i + '">Delete</button></td></tr>';
        });
        if (!DB[k].length) html = '<tr><td colspan="5" class="text-center text-muted">No records found.</td></tr>';
        $(CFG[k].rows).html(html);
        $(CFG[k].count).text(DB[k].length);
      });
    };
    var load = function () {
      var sort = $("#sortSel").val();
      call("GET", "api/admin/accounts", { role: "student", sort: sort }).then(function (a) {
        if (a.auth === false) { window.location.href = "login.html"; return; }
        DB.user = a.records;
        $("#undoCount").text(a.undoCount);
        call("GET", "api/admin/accounts", { role: "staff", sort: sort }).then(function (b) { DB.staff = b.records; render(); });
      });
    };
    var openForm = function (k, i) {
      mode = k; editIndex = i;
      var c = CFG[k], r = i > -1 ? DB[k][i] : { id: "", name: "", email: "", extra: c.options[0] };
      $("#modalTitle").text((i > -1 ? "Edit " : "Add ") + c.label);
      $("#lblId").text(c.idLabel); $("#lblExtra").text(c.extraLabel);
      $("#fExtra").html(c.options.map(function (o) { return "<option>" + o + "</option>"; }).join(""));
      $("#fId").val(r.id); $("#fName").val(r.name); $("#fEmail").val(r.email); $("#fExtra").val(r.extra); $("#fPass").val("");
      $("#passHint").text(i > -1 ? "(leave blank to keep current)" : "");
      $("#formAlert").addClass("d-none");
      modal.show();
    };
    $("#sortSel").on("change", load);
    $("#undoBtn").on("click", function () {
      call("POST", "api/admin/undo").then(function (r) { flash(r.message, !r.ok); load(); });
    });
    $(document).on("click", "[data-add]", function () { openForm($(this).data("add"), -1); });
    $(document).on("click", "[data-edit]", function () { var p = $(this).data("edit").split(":"); openForm(p[0], +p[1]); });
    $(document).on("click", "[data-del]", function () {
      var p = $(this).data("del").split(":"), rec = DB[p[0]][+p[1]];
      if (window.confirm("Delete this " + CFG[p[0]].label.toLowerCase() + " record?")) {
        call("POST", "api/admin/delete", { role: CFG[p[0]].role, id: rec.id }).then(function (r) { flash(r.message, !r.ok); load(); });
      }
    });
    $("#recordForm").on("submit", function (e) {
      e.preventDefault();
      var data = { role: CFG[mode].role, id: $.trim($("#fId").val()), name: $.trim($("#fName").val()), email: $.trim($("#fEmail").val()), extra: $("#fExtra").val(), password: $("#fPass").val() };
      var url = "api/admin/add";
      if (editIndex > -1) { url = "api/admin/update"; data.oldId = DB[mode][editIndex].id; }
      call("POST", url, data).then(function (r) {
        if (!r.ok) { $("#formAlert").text(r.message).removeClass("d-none"); return; }
        modal.hide(); flash(r.message, false); load();
      });
    });
    // Administrative enrollment and credential review use the same Java API and database.
    var enrollmentStatuses = ["Pending Review", "Requirements Incomplete", "Approved", "Rejected"];
    var credentialStatuses = ["Pending Review", "Verified", "Needs Resubmission", "Rejected"];
    var statusOptions = function (items, selected) {
      return items.map(function (value) { return '<option' + (value === selected ? ' selected' : '') + '>' + esc(value) + '</option>'; }).join("");
    };
    var loadEnrollments = function () {
      if (!$("#enrollmentRecords").length) return;
      call("GET", "api/admin/enrollments").then(function (r) {
        if (!r.records) { $("#enrollmentRecords").html('<div class="alert alert-warning">' + esc(r.message || "Enrollment records could not be loaded.") + '</div>'); return; }
        if (!r.records.length) { $("#enrollmentRecords").html('<p class="text-muted">No enrollment transactions have been submitted yet.</p>'); return; }
        var html = '<table class="table table-bordered align-middle bg-white"><thead><tr><th>Student</th><th>Submitted</th><th>Selected subjects</th><th>Units</th><th>Queue</th><th>Status</th><th>Registrar remarks</th></tr></thead><tbody>';
        r.records.forEach(function (record) {
          var subjects = (record.subjects || []).map(function (sub) {
            return '<div class="mb-1"><strong>' + esc(sub.code) + '</strong> ' + esc(sub.title) + '<div class="small text-secondary">' + esc(sub.section) + ' · ' + esc(sub.schedule) + ' · ' + sub.units + ' units</div></div>';
          }).join("");
          html += '<tr><td><strong>' + esc(record.studentName) + '</strong><div class="small text-secondary">' + esc(record.studentId) + '</div></td>' +
            '<td>' + esc(record.submittedAt) + '</td><td>' + (subjects || 'No subjects listed') + '</td><td>' + record.totalUnits + '</td><td>' + record.queueNumber + '</td>' +
            '<td><select class="form-select form-select-sm" data-enrollment-status="' + esc(record.id) + '">' + statusOptions(enrollmentStatuses, record.status) + '</select></td>' +
            '<td style="min-width:220px"><input class="form-control form-control-sm mb-2" maxlength="1000" placeholder="Optional remarks" data-enrollment-note="' + esc(record.id) + '" value="' + esc(record.adminNotes) + '"><button type="button" class="btn btn-sm btn-primary" data-save-enrollment="' + esc(record.id) + '">Save review</button></td></tr>';
        });
        $("#enrollmentRecords").html(html + '</tbody></table>');
      });
    };
    var loadCredentials = function () {
      if (!$("#credentialRecords").length) return;
      call("GET", "api/admin/credentials").then(function (r) {
        if (!r.credentials) { $("#credentialRecords").html('<div class="alert alert-warning">' + esc(r.message || "Credentials could not be loaded.") + '</div>'); return; }
        if (!r.credentials.length) { $("#credentialRecords").html('<p class="text-muted">No student requirements have been submitted yet.</p>'); return; }
        var html = '<table class="table table-bordered align-middle bg-white"><thead><tr><th>Student</th><th>Requirement</th><th>Document</th><th>Submitted</th><th>Review status</th><th>Remarks</th></tr></thead><tbody>';
        r.credentials.forEach(function (credential) {
          html += '<tr><td><strong>' + esc(credential.studentName) + '</strong><div class="small text-secondary">' + esc(credential.studentId) + '</div></td>' +
            '<td>' + esc(credential.type) + '</td><td><button type="button" class="btn btn-sm btn-outline-primary" data-view-credential="' + esc(credential.id) + '">View document</button><div class="small text-secondary mt-1">' + esc(credential.fileName) + '</div></td>' +
            '<td>' + esc(credential.submittedAt) + '</td><td><select class="form-select form-select-sm" data-credential-status="' + esc(credential.id) + '">' + statusOptions(credentialStatuses, credential.status) + '</select></td>' +
            '<td style="min-width:220px"><input class="form-control form-control-sm mb-2" maxlength="1000" placeholder="Optional remarks" data-credential-note="' + esc(credential.id) + '" value="' + esc(credential.adminNotes) + '"><button type="button" class="btn btn-sm btn-primary" data-save-credential="' + esc(credential.id) + '">Save review</button></td></tr>';
        });
        $("#credentialRecords").html(html + '</tbody></table>');
      });
    };
    $(document).on("click", "[data-save-enrollment]", function () {
      var id = $(this).attr("data-save-enrollment"), $button = $(this);
      $button.prop("disabled", true);
      call("POST", "api/admin/enrollment/review", { id: id, status: $('[data-enrollment-status="' + id + '"]').val(), notes: $('[data-enrollment-note="' + id + '"]').val() })
        .then(function (r) { flash(r.message, !r.ok); loadEnrollments(); })
        .always(function () { $button.prop("disabled", false); });
    });
    $(document).on("click", "[data-save-credential]", function () {
      var id = $(this).attr("data-save-credential"), $button = $(this);
      $button.prop("disabled", true);
      call("POST", "api/admin/credentials/review", { id: id, status: $('[data-credential-status="' + id + '"]').val(), notes: $('[data-credential-note="' + id + '"]').val() })
        .then(function (r) { flash(r.message, !r.ok); loadCredentials(); })
        .always(function () { $button.prop("disabled", false); });
    });
    $(document).on("click", "[data-view-credential]", function () {
      var id = $(this).attr("data-view-credential");
      var preview = window.open("about:blank", "_blank");
      if (!preview) { flash("Allow pop-ups to open a submitted document.", true); return; }
      preview.document.write("<title>Loading document</title><p style='font-family:Arial;padding:2rem'>Loading submitted document...</p>");
      call("GET", "api/admin/credentials/file", { id: id }).then(function (r) {
        if (!r.ok) { preview.close(); flash(r.message || "Document could not be opened.", true); return; }
        try {
          var bytes = atob(r.data), nums = new Uint8Array(bytes.length);
          for (var i = 0; i < bytes.length; i++) nums[i] = bytes.charCodeAt(i);
          var url = URL.createObjectURL(new Blob([nums], { type: r.contentType }));
          preview.location.href = url;
          setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
        } catch (err) { preview.close(); flash("This document could not be previewed in the browser.", true); }
      });
    });
    var renderApplications = function () {
      call("GET", "api/admin/applications").then(function (r) {
        if (!r.applications) { $("#applicationRecords").html('<div class="alert alert-warning">' + esc(r.message || "Applications could not be loaded.") + '</div>'); return; }
        if (!r.applications.length) { $("#applicationRecords").html('<p class="text-muted">No pending student applications.</p>'); return; }
        call("GET", "api/admin/academic/programs").then(function (programResult) {
          var programs = programResult.programs || [], html = '<table class="table table-bordered align-middle bg-white"><thead><tr><th>Applicant</th><th>Classification / Previous studies</th><th>Program & curriculum</th><th>Application status</th><th>Official requirements checklist</th><th>Registrar remarks</th></tr></thead><tbody>';
          r.applications.forEach(function (app, index) {
            var programOptions = '<option value="">Choose program</option>' + programs.map(function (v) { return '<option value="' + esc(v.id) + '"' + (v.id === app.programId ? ' selected' : '') + '>' + esc(v.code + " - " + v.name) + '</option>'; }).join("");
            html += '<tr><td><strong>' + esc(app.studentName) + '</strong><div class="small text-secondary">' + esc(app.studentId) + ' · ' + esc(app.email) + '</div></td><td>' + esc(app.studentType) + (app.studentType === "Transferee" ? '<div class="small text-secondary">Previous school: ' + esc(app.previousSchool) + '<br>Previous program: ' + esc(app.previousProgram) + '</div>' : '') + '</td><td style="min-width:210px"><select class="form-select form-select-sm mb-2" data-app-program="' + esc(app.studentId) + '">' + programOptions + '</select><select class="form-select form-select-sm" data-app-curriculum="' + esc(app.studentId) + '"><option value="">Loading curricula...</option></select></td><td style="min-width:175px"><select class="form-select form-select-sm" data-app-status="' + esc(app.studentId) + '">' + statusOptions(["REQUIREMENTS_PENDING","ACADEMIC_EVALUATION","APPROVED","REJECTED"], app.status) + '</select></td><td style="min-width:190px"><label class="small"><input class="form-check-input me-1" type="checkbox" data-app-requirements="' + esc(app.studentId) + '"' + (app.requirementsConfirmed ? ' checked' : '') + '> I checked the complete official requirements checklist</label></td><td style="min-width:210px"><input class="form-control form-control-sm mb-2" maxlength="1000" data-app-notes="' + esc(app.studentId) + '" placeholder="Evaluation / requirements remarks" value="' + esc(app.adminNotes) + '"><button class="btn btn-sm btn-primary" type="button" data-save-application="' + esc(app.studentId) + '">Save application</button></td></tr>';
          });
          $("#applicationRecords").html(html + '</tbody></table>');
          r.applications.forEach(function (app) {
            var id = app.studentId, selectedProgram = app.programId || $('[data-app-program="' + id + '"] option:first').val();
            call("GET", "api/admin/academic/curricula", { programId: selectedProgram }).then(function (cr) {
              var options = '<option value="">Choose curriculum</option>';
              (cr.curricula || []).forEach(function (c) { options += '<option value="' + esc(c.id) + '"' + (c.id === app.curriculumId ? ' selected' : '') + '>' + esc(c.name + " · " + c.effectiveYear) + '</option>'; });
              $('[data-app-curriculum="' + id + '"]').html(options);
            });
          });
        });
      });
    };
    var loadAcademicSetup = function () {
      call("GET", "api/admin/academic/programs").then(function (r) {
        var options = (r.programs || []).map(function (p) { return '<option value="' + esc(p.id) + '">' + esc(p.code + " - " + p.name) + '</option>'; }).join("");
        $("#curriculumProgram, #sectionProgram").html(options);
        var programId = $("#sectionProgram").val() || (r.programs && r.programs.length ? r.programs[0].id : "");
        loadAcademicCurricula(programId);
      });
      call("GET", "api/admin/academic/subjects").then(function (r) {
        var html = (r.subjects || []).map(function (v) { return '<option value="' + esc(v.code) + '">' + esc(v.code + " - " + v.title) + '</option>'; }).join("");
        $("#sectionSubject").html(html);
      });
      call("GET", "api/admin/academic/sections").then(function (r) {
        var html = '<table class="table table-bordered bg-white"><thead><tr><th>Subject</th><th>Section</th><th>Program</th><th>Lec/Lab</th><th>Minimum</th><th>Maximum</th><th>Enrolled</th><th>Remaining</th><th>Action</th></tr></thead><tbody>';
        (r.sections || []).forEach(function (v) { html += '<tr><td>' + esc(v.code + " - " + v.title) + '</td><td>' + esc(v.section) + '</td><td>' + esc(v.programId) + '</td><td>' + v.lectureUnits + ' / ' + v.labUnits + '</td><td><input class="form-control form-control-sm" type="number" min="0" data-capacity-min="' + esc(v.id) + '" value="' + v.minimum + '"></td><td><input class="form-control form-control-sm" type="number" min="1" data-capacity-max="' + esc(v.id) + '" value="' + v.maximum + '"></td><td>' + v.enrolled + '</td><td>' + Math.max(0, v.maximum-v.enrolled) + '</td><td><button class="btn btn-sm btn-primary" data-save-capacity="' + esc(v.id) + '">Save</button></td></tr>'; });
        $("#academicSections").html(html + '</tbody></table>');
      });
    };
    var loadAcademicCurricula = function (programId) {
      call("GET", "api/admin/academic/curricula", { programId: programId }).then(function (r) {
        var options = (r.curricula || []).map(function (c) { return '<option value="' + esc(c.id) + '">' + esc(c.name + " · " + c.effectiveYear) + '</option>'; }).join("");
        $("#sectionCurriculum").html(options);
      });
    };
    $("#sectionProgram").on("change", function () { loadAcademicCurricula($(this).val()); });
    $(document).on("click", "[data-save-capacity]", function () {
      var id=$(this).attr("data-save-capacity");
      call("POST","api/admin/academic/section/capacity",{offeringId:id,minimum:$('[data-capacity-min="'+id+'"]').val(),maximum:$('[data-capacity-max="'+id+'"]').val()}).then(function(r){$("#academicMsg").html('<div class="alert alert-'+(r.ok?"success":"danger")+'">'+esc(r.message)+'</div>');if(r.ok)loadAcademicSetup();});
    });
    $("#programForm").on("submit", function (e) { e.preventDefault(); call("POST", "api/admin/academic/program", { code: $("#programCode").val(), name: $("#programName").val() }).then(function (r) { $("#academicMsg").html('<div class="alert alert-' + (r.ok ? "success" : "danger") + '">' + esc(r.message) + '</div>'); if (r.ok) { this.reset(); loadAcademicSetup(); renderApplications(); } }.bind(this)); });
    $("#curriculumForm").on("submit", function (e) { e.preventDefault(); call("POST", "api/admin/academic/curriculum", { programId: $("#curriculumProgram").val(), name: $("#curriculumName").val(), effectiveYear: $("#curriculumYear").val() }).then(function (r) { $("#academicMsg").html('<div class="alert alert-' + (r.ok ? "success" : "danger") + '">' + esc(r.message) + '</div>'); if (r.ok) { this.reset(); loadAcademicSetup(); renderApplications(); } }.bind(this)); });
    $("#academicSubjectForm").on("submit", function (e) { e.preventDefault(); call("POST", "api/admin/academic/subject", { code: $("#academicSubjectCode").val(), title: $("#academicSubjectTitle").val(), prerequisites: $("#academicPrerequisites").val() }).then(function (r) { $("#academicMsg").html('<div class="alert alert-' + (r.ok ? "success" : "danger") + '">' + esc(r.message) + '</div>'); if (r.ok) { this.reset(); loadAcademicSetup(); } }.bind(this)); });
    $("#sectionForm").on("submit", function (e) { e.preventDefault(); call("POST", "api/admin/academic/section", { subjectCode: $("#sectionSubject").val(), programId: $("#sectionProgram").val(), curriculumId: $("#sectionCurriculum").val(), section: $("#sectionName").val(), lectureUnits: $("#lecUnits").val(), labUnits: $("#labUnits").val(), minimum: $("#minStudents").val(), maximum: $("#maxStudents").val(), lectureDay: $("#lecDay").val(), lectureStart: $("#lecStart").val(), lectureEnd: $("#lecEnd").val(), labDay: $("#labDay").val(), labStart: $("#labStart").val(), labEnd: $("#labEnd").val() }).then(function (r) { $("#academicMsg").html('<div class="alert alert-' + (r.ok ? "success" : "danger") + '">' + esc(r.message) + '</div>'); if (r.ok) { this.reset(); loadAcademicSetup(); } }.bind(this)); });
    $(document).on("change", "[data-app-program]", function () {
      var id = $(this).attr("data-app-program");
      call("GET", "api/admin/academic/curricula", { programId: $(this).val() }).then(function (r) {
        var html = '<option value="">Choose curriculum</option>';
        (r.curricula || []).forEach(function (c) { html += '<option value="' + esc(c.id) + '">' + esc(c.name + " · " + c.effectiveYear) + '</option>'; });
        $('[data-app-curriculum="' + id + '"]').html(html);
      });
    });
    $(document).on("click", "[data-save-application]", function () {
      var id = $(this).attr("data-save-application");
      call("POST", "api/admin/application/review", { studentId: id, status: $('[data-app-status="' + id + '"]').val(), programId: $('[data-app-program="' + id + '"]').val(), curriculumId: $('[data-app-curriculum="' + id + '"]').val(), requirementsConfirmed: $('[data-app-requirements="' + id + '"]').is(":checked"), notes: $('[data-app-notes="' + id + '"]').val() }).then(function (r) { flash(r.message, !r.ok); renderApplications(); });
    });
    loadEnrollments(); loadCredentials(); renderApplications(); loadAcademicSetup();

    load();
  }

  // =================== STAFF WORKSPACE ===================
  function startStaff(me) {
    var position = me.position || "";
    $("#staffName").text(me.name || ""); $("#staffPosition").text(position);
    var canRegistrar = position === "Registrar" || position === "Administrator" || position === "Clerk";
    var canReview = position === "Registrar" || position === "Administrator";
    var canCashier = position === "Cashier" || position === "Administrator";
    if (!canRegistrar) $('[data-role-panel="registrar"]').addClass("d-none");
    if (!canCashier) $('[data-role-panel="cashier"]').addClass("d-none");
    if (!canRegistrar && canCashier) bootstrap.Tab.getOrCreateInstance($('[data-bs-target="#staffQueue"]')[0]).show();
    var flashStaff = function (message, bad) { $("#staffMsg").html('<div class="alert alert-' + (bad ? "danger" : "success") + '">' + esc(message) + '</div>'); };
    var staffStatusOptions = function (items, selected) { return items.map(function (value) { return '<option' + (value === selected ? ' selected' : '') + '>' + esc(value) + '</option>'; }).join(""); };
    var renderStaffApplications = function () {
      call("GET", "api/staff/applications").then(function (r) {
        if (!r.applications) { $("#staffApplicationsList").html('<p class="text-muted">' + esc(r.message || "No access.") + '</p>'); return; }
        if (!r.applications.length) { $("#staffApplicationsList").html('<p class="text-muted">No pending applications.</p>'); return; }
        call("GET", "api/programs").then(function (pr) {
          var html = '<table class="table table-bordered bg-white"><thead><tr><th>Applicant</th><th>Classification / Previous studies</th><th>Program & curriculum</th><th>Status</th><th>Official requirements checklist</th><th>Remarks</th></tr></thead><tbody>';
          r.applications.forEach(function (a) {
            var opts = '<option value="">Choose program</option>' + (pr.programs || []).map(function (p) { return '<option value="' + esc(p.id) + '"' + (p.id === a.programId ? ' selected' : '') + '>' + esc(p.code + " - " + p.name) + '</option>'; }).join("");
            html += '<tr><td>' + esc(a.studentName) + '<div class="small text-secondary">' + esc(a.studentId) + '</div></td><td>' + esc(a.studentType) + (a.studentType === "Transferee" ? '<div class="small text-secondary">' + esc(a.previousSchool) + ' · ' + esc(a.previousProgram) + '</div>' : '') + '</td><td style="min-width:190px"><select class="form-select form-select-sm mb-2" data-staff-program="' + esc(a.studentId) + '">' + opts + '</select><select class="form-select form-select-sm" data-staff-curr="' + esc(a.studentId) + '"><option value="">Choose curriculum</option></select></td><td><select class="form-select form-select-sm" data-staff-status="' + esc(a.studentId) + '">' + staffStatusOptions(["REQUIREMENTS_PENDING","ACADEMIC_EVALUATION","APPROVED","REJECTED"], a.status) + '</select></td><td style="min-width:190px"><label class="small"><input class="form-check-input me-1" type="checkbox" data-staff-req="' + esc(a.studentId) + '"' + (a.requirementsConfirmed ? ' checked' : '') + '> Official checklist checked</label></td><td style="min-width:200px"><input class="form-control form-control-sm mb-2" data-staff-notes="' + esc(a.studentId) + '" value="' + esc(a.adminNotes) + '"><button class="btn btn-sm btn-primary" type="button" data-staff-save-app="' + esc(a.studentId) + '">Save</button></td></tr>';
          });
          $("#staffApplicationsList").html(html + '</tbody></table>');
        if (!canReview) $("#staffApplicationsList select, #staffApplicationsList input, #staffApplicationsList button").prop("disabled", true);
          r.applications.forEach(function (a) {
            var id = a.studentId, prog = a.programId || $('[data-staff-program="' + id + '"] option:first').val();
            call("GET", "api/curricula", { programId: prog }).then(function (cr) {
              var opts = '<option value="">Choose curriculum</option>';
              (cr.curricula || []).forEach(function (c) { opts += '<option value="' + esc(c.id) + '"' + (c.id === a.curriculumId ? ' selected' : '') + '>' + esc(c.name + " · " + c.effectiveYear) + '</option>'; });
              $('[data-staff-curr="' + id + '"]').html(opts);
            });
          });
        });
      });
    };
    var renderStaffCredentials = function () {
      call("GET", "api/staff/credentials").then(function (r) {
        if (!r.credentials) { $("#staffCredentialsList").html('<p class="text-muted">' + esc(r.message || "No access.") + '</p>'); return; }
        var html = '<table class="table table-bordered bg-white"><thead><tr><th>Student</th><th>Document</th><th>Status</th><th>Remarks</th></tr></thead><tbody>';
        (r.credentials || []).forEach(function (c) { html += '<tr><td>' + esc(c.studentName) + '<div class="small text-secondary">' + esc(c.studentId) + '</div></td><td>' + esc(c.type) + '<br><button class="btn btn-sm btn-outline-primary mt-1" data-staff-view-doc="' + esc(c.id) + '">View ' + esc(c.fileName) + '</button></td><td><select class="form-select form-select-sm" data-staff-cred-status="' + esc(c.id) + '">' + staffStatusOptions(["Pending Review","Verified","Needs Resubmission","Rejected"],c.status) + '</select></td><td><input class="form-control form-control-sm mb-2" data-staff-cred-note="' + esc(c.id) + '" value="' + esc(c.adminNotes) + '"><button class="btn btn-sm btn-primary" data-staff-save-cred="' + esc(c.id) + '">Save review</button></td></tr>'; });
        $("#staffCredentialsList").html(html + '</tbody></table>');
        if (!canReview) $("#staffCredentialsList select, #staffCredentialsList input, [data-staff-save-cred]").prop("disabled", true);
      });
    };
    var renderStaffEnrollments = function () {
      call("GET", "api/staff/enrollments").then(function (r) {
        if (!r.records) { $("#staffEnrollmentList").html('<p class="text-muted">' + esc(r.message || "No access.") + '</p>'); return; }
        var html = '<table class="table table-bordered bg-white"><thead><tr><th>Student</th><th>Submitted</th><th>Subjects</th><th>Units</th><th>Status</th></tr></thead><tbody>';
        (r.records || []).forEach(function (v) { var subs=(v.subjects||[]).map(function(x){return esc(x.code+" "+x.title+" ("+x.lectureUnits+" Lec / "+x.labUnits+" Lab)")+" · "+esc(x.schedule);}).join("<br>"); html += '<tr><td>' + esc(v.studentName) + '<div class="small text-secondary">' + esc(v.studentId) + '</div></td><td>' + esc(v.submittedAt) + '</td><td>' + subs + '</td><td>' + v.totalUnits + '</td><td><select class="form-select form-select-sm mb-2" data-staff-enroll-status="' + esc(v.id) + '">' + staffStatusOptions(["Pending Review","Requirements Incomplete","Approved","Rejected"],v.status) + '</select><input class="form-control form-control-sm mb-2" data-staff-enroll-note="' + esc(v.id) + '" value="' + esc(v.adminNotes) + '"><button class="btn btn-sm btn-primary" data-staff-save-enroll="' + esc(v.id) + '">Save</button></td></tr>'; });
        $("#staffEnrollmentList").html(html + '</tbody></table>');
        if (!canReview) $("#staffEnrollmentList select, #staffEnrollmentList input, [data-staff-save-enroll]").prop("disabled", true);
      });
    };
    $(document).on("change", "[data-staff-program]", function () {
      var id=$(this).attr("data-staff-program"); call("GET","api/curricula",{programId:$(this).val()}).then(function(r){var h='<option value="">Choose curriculum</option>';(r.curricula||[]).forEach(function(c){h+='<option value="'+esc(c.id)+'">'+esc(c.name+" · "+c.effectiveYear)+'</option>';}); $('[data-staff-curr="'+id+'"]').html(h);});
    });
    $(document).on("click", "[data-staff-save-app]", function () {
      var id=$(this).attr("data-staff-save-app"); call("POST","api/staff/application/review",{studentId:id,status:$('[data-staff-status="'+id+'"]').val(),programId:$('[data-staff-program="'+id+'"]').val(),curriculumId:$('[data-staff-curr="'+id+'"]').val(),requirementsConfirmed:$('[data-staff-req="'+id+'"]').is(":checked"),notes:$('[data-staff-notes="'+id+'"]').val()}).then(function(r){flashStaff(r.message,!r.ok);renderStaffApplications();});
    });
    $(document).on("click", "[data-staff-save-cred]", function () {
      var id=$(this).attr("data-staff-save-cred");
      call("POST","api/staff/credentials/review",{id:id,status:$('[data-staff-cred-status="'+id+'"]').val(),notes:$('[data-staff-cred-note="'+id+'"]').val()}).then(function(r){flashStaff(r.message,!r.ok);renderStaffCredentials();});
    });
    $(document).on("click", "[data-staff-save-enroll]", function () {
      var id=$(this).attr("data-staff-save-enroll");
      call("POST","api/staff/enrollment/review",{id:id,status:$('[data-staff-enroll-status="'+id+'"]').val(),notes:$('[data-staff-enroll-note="'+id+'"]').val()}).then(function(r){flashStaff(r.message,!r.ok);renderStaffEnrollments();});
    });
    $(document).on("click", "[data-staff-view-doc]", function () {
      var id=$(this).attr("data-staff-view-doc"), win=window.open("about:blank","_blank"); if(!win){flashStaff("Allow pop-ups to preview documents.",true);return;}
      call("GET","api/staff/credentials/file",{id:id}).then(function(r){if(!r.ok){win.close();flashStaff(r.message,true);return;}var bytes=atob(r.data),nums=new Uint8Array(bytes.length);for(var i=0;i<bytes.length;i++)nums[i]=bytes.charCodeAt(i);win.location.href=URL.createObjectURL(new Blob([nums],{type:r.contentType}));});
    });
    $("#staffCallNext").on("click", function () { call("POST","api/staff/queue/next").then(function(r){if(!r.ok){flashStaff(r.message,true);return;}$("#staffNowServing").text(r.nowServing);}); });
    if (canRegistrar) { renderStaffApplications(); renderStaffCredentials(); renderStaffEnrollments(); }
    if (canCashier) call("GET","api/staff/queue").then(function(r){$("#staffNowServing").text(r.nowServing || "-");});
  }

  // =================== ENROLLMENT ===================
  function startEnrollment() {
    var showMsg = function (text, type) { $("#subjectMessage").html('<div class="alert alert-' + type + '">' + esc(text) + "</div>"); };
    var loadSubjects, loadSelected, loadQueue, loadSlips, pollTimer = null;

    loadSubjects = function () {
      call("GET", "api/subjects").then(function (r) {
        if (r.auth === false) { window.location.href = "login.html"; return; }
        if (r.locked) { $("#applicationStatus").text(r.message).removeClass("alert-success").addClass("alert-warning"); $("#enrollmentWorkspace").addClass("d-none"); return; }
        $("#academicAssignment").text("Assigned program and curriculum: " + r.programId + " · " + r.curriculumId);
        $("#termLine").text(r.term);
        $("#maxUnits").text(r.maxUnits); $("#selectedUnits").text(r.units);
        $("#noticeText").html(r.notice ? esc(r.notice) : "No failed subjects. Everything is open to you.");
        var html = "";
        r.subjects.forEach(function (s) {
          var picked = s.status === "Selected", blocked = (s.status !== "Available" && !picked) || r.submitted;
          var badge = (s.status === "Available" || picked) ? "success" : "danger";
          html += "<tr><td>" + esc(s.code) + "</td><td>" + esc(s.title) + "</td><td>" + s.lectureUnits + "</td><td>" + s.labUnits + "</td><td>" + s.units + "</td><td>" + esc(s.section) + "</td><td>" + esc(s.schedule) + "</td><td>" + s.minimumStudents + "</td><td>" + s.maximumStudents + "</td><td>" + s.enrolledStudents + "</td><td>" + s.slots +
            '</td><td><span class="badge text-bg-' + (s.status === "Full" ? "danger" : (s.sectionStatus === "Below minimum" ? "warning" : badge)) + '">' + esc(s.status === "Available" ? s.sectionStatus : s.status) + '</span></td><td><input class="form-check-input subj" type="checkbox" aria-label="Select ' + esc(s.code) + ' ' + esc(s.title) + ' section ' + esc(s.section) + '" data-offering-id="' + esc(s.offeringId) + '"' +
            (picked ? " checked" : "") + (blocked ? " disabled" : "") + "></td></tr>";
        });
        $("#subjectRows").html(html);
        $("#submitEnroll").prop("disabled", r.submitted);
      });
    };
    loadSelected = function () {
      call("GET", "api/selected").then(function (r) {
        if (!r.timetable || !r.timetable.length) { $("#timetableBox").html('<span class="text-muted">No subjects selected yet.</span>'); return; }
        var html = '<table class="table table-bordered table-sm text-center mb-0"><thead><tr><th>Time</th>';
        r.days.forEach(function (d) { html += "<th>" + d + "</th>"; });
        html += "</tr></thead><tbody>";
        r.timetable.forEach(function (row) {
          html += "<tr><td>" + row.time + "</td>";
          row.cells.forEach(function (c) { html += c ? '<td class="event">' + esc(c) + "</td>" : "<td></td>"; });
          html += "</tr>";
        });
        $("#timetableBox").html(html + "</tbody></table>");
      });
    };
    loadQueue = function () {
      call("GET", "api/queue").then(function (q) {
        clearTimeout(pollTimer);
        if (!q.submitted) { return; }
        $("#paymentLocked").addClass("d-none"); $("#paymentContent").removeClass("d-none");
        $("#queueNumber").text(q.myNumber); $("#numbersAhead").text(q.ahead); $("#currentNumber").text(q.nowServing);
        $("#turnStatus").text(q.status); $("#queueMessage").text(q.message);
        $("#nextNumber").prop("disabled", q.ahead === 0 || q.cashierRunning);
        $("#startCashier").prop("disabled", q.ahead === 0 || q.cashierRunning);
        if (q.cashierRunning) { pollTimer = setTimeout(loadQueue, 1000); } // the cashier thread is working; keep checking
      });
    };
    loadSlips = function () {
      call("GET", "api/slips").then(function (r) {
        if (!r.slips || !r.slips.length) { $("#slipList").html('<span class="text-muted">No saved enrollment slip yet.</span>'); return; }
        var html = "";
        r.slips.forEach(function (s) {
          html += '<div class="panel mb-3"><strong>Submitted ' + esc(s.submittedAt) + "</strong> &middot; Queue No. " + s.queueNumber + " &middot; " + s.totalUnits + " units" +
            ' <span class="badge text-bg-' + (s.status === "Approved" ? "success" : (s.status === "Rejected" || s.status === "Requirements Incomplete" ? "warning" : "secondary")) + '">' + esc(s.status || "Pending Review") + '</span>' +
            (s.adminNotes ? '<p class="small text-secondary mt-2 mb-0">Registrar remarks: ' + esc(s.adminNotes) + '</p>' : '') + '<ul class="mb-0 mt-2">';
          s.subjects.forEach(function (x) { html += "<li>" + esc(x.code) + " " + esc(x.title) + " (" + x.units + " units, " + esc(x.schedule) + ")</li>"; });
          html += "</ul></div>";
        });
        $("#slipList").html(html);
      });
    };

    $(document).on("change", ".subj", function () {
      var box = $(this), url = box.is(":checked") ? "api/select" : "api/remove";
      call("POST", url, { offeringId: box.attr("data-offering-id") }).then(function (r) {
        showMsg(r.message, r.ok ? "success" : "danger");
        loadSubjects(); loadSelected();
      });
    });
    $("#searchBtn").on("click", function () {
      call("GET", "api/subjects/search", { code: $("#searchCode").val() }).then(function (r) {
        if (!r.ok) { $("#searchResult").html('<div class="alert alert-warning mb-0">' + esc(r.message) + "</div>"); return; }
        var s = r.subject;
        $("#searchResult").html('<div class="alert alert-info mb-0"><strong>' + esc(s.code) + "</strong> " + esc(s.title) + " | " + s.units + " units | " + esc(s.schedule) +
          " | Status: " + esc(s.status) + "<br>Prerequisites: " + (r.prerequisites.length ? esc(r.prerequisites.join(", ")) : "none") + "</div>");
      });
    });
    $("#submitEnroll").on("click", function () {
      call("POST", "api/submit").then(function (r) {
        showMsg(r.message, r.ok ? "success" : (r.message.indexOf("least one") > -1 ? "warning" : "danger"));
        loadSubjects(); loadQueue(); loadSlips();
      });
    });
    $("#nextNumber").on("click", function () { call("POST", "api/queue/next").then(loadQueue); });
    $("#startCashier").on("click", function () { call("POST", "api/queue/cashier").then(loadQueue); });

    var loadStudentCredentials = function () {
      if (!$("#studentCredentials").length) return;
      call("GET", "api/credentials").then(function (r) {
        if (!r.credentials) { $("#studentCredentials").html('<div class="alert alert-warning">' + esc(r.message || "Requirements could not be loaded.") + '</div>'); return; }
        if (!r.credentials.length) { $("#studentCredentials").html('<p class="text-muted">No requirements submitted yet.</p>'); return; }
        var html = '<div class="table-responsive"><table class="table table-bordered align-middle bg-white"><thead><tr><th>Requirement</th><th>File</th><th>Submitted</th><th>Status</th><th>Registrar remarks</th></tr></thead><tbody>';
        r.credentials.forEach(function (doc) {
          var badge = doc.status === "Verified" ? "success" : (doc.status === "Rejected" || doc.status === "Needs Resubmission" ? "warning" : "secondary");
          html += '<tr><td>' + esc(doc.type) + '</td><td>' + esc(doc.fileName) + '</td><td>' + esc(doc.submittedAt) + '</td><td><span class="badge text-bg-' + badge + '">' + esc(doc.status) + '</span></td><td>' + esc(doc.adminNotes || "No remarks") + '</td></tr>';
        });
        $("#studentCredentials").html(html + '</tbody></table></div>');
      });
    };
    $("#uploadCredential").on("click", function () {
      var fileInput = $("#credentialFile")[0], file = fileInput && fileInput.files ? fileInput.files[0] : null;
      var type = $("#credentialType").val();
      $("#credentialMessage").empty();
      if (!file) { $("#credentialMessage").html('<div class="alert alert-warning">Choose a document first.</div>'); return; }
      if (file.size > 2 * 1024 * 1024) { $("#credentialMessage").html('<div class="alert alert-warning">The document must be 2 MB or smaller.</div>'); return; }
      var allowed = ["application/pdf", "image/png", "image/jpeg"];
      if (allowed.indexOf(file.type) < 0) { $("#credentialMessage").html('<div class="alert alert-warning">Choose a PDF, PNG, or JPG/JPEG file.</div>'); return; }
      var reader = new FileReader(), $button = $(this);
      $button.prop("disabled", true).text("Submitting...");
      reader.onload = function () {
        var result = String(reader.result || ""), comma = result.indexOf(",");
        if (comma < 0) { $("#credentialMessage").html('<div class="alert alert-danger">The document could not be read.</div>'); $button.prop("disabled", false).text("Submit requirement"); return; }
        call("POST", "api/credentials/upload", { type: type, fileName: file.name, contentType: file.type, data: result.substring(comma + 1) }).then(function (r) {
          $("#credentialMessage").html('<div class="alert alert-' + (r.ok ? "success" : "danger") + '">' + esc(r.message) + '</div>');
          if (r.ok) { fileInput.value = ""; loadStudentCredentials(); }
        }).always(function () { $button.prop("disabled", false).text("Submit requirement"); });
      };
      reader.onerror = function () { $("#credentialMessage").html('<div class="alert alert-danger">The document could not be read.</div>'); $button.prop("disabled", false).text("Submit requirement"); };
      reader.readAsDataURL(file);
    });

    call("GET", "api/application/status").then(function (r) {
      var app = r.application || {}, status = app.status || "APPLICATION_NOT_FOUND";
      var messages = {
        REQUIREMENTS_PENDING: "Your documents are awaiting registrar verification. Schedule selection is locked until approval.",
        ACADEMIC_EVALUATION: "Your transferee application is awaiting curriculum evaluation. Schedule selection is locked until the registrar assigns and approves your academic profile.",
        REJECTED: "Your application was not approved. Please review the registrar remarks and contact the registrar's office.",
        APPROVED: "Your requirements and academic profile are approved. You may proceed with subject and schedule selection."
      };
      $("#applicationStatus").text(messages[status] || "Your application status is being checked by the registrar.")
        .toggleClass("alert-success", status === "APPROVED")
        .toggleClass("alert-warning", status !== "APPROVED");
      if (app.adminNotes) $("#applicationStatus").append(document.createTextNode(" Registrar remarks: " + app.adminNotes));
      if (status === "APPROVED" && app.programId && app.curriculumId) {
        $("#enrollmentWorkspace").removeClass("d-none");
        loadSubjects(); loadSelected(); loadQueue(); loadSlips();
      } else {
        $("#enrollmentWorkspace").addClass("d-none");
      }
    });
    loadStudentCredentials();
  }
});
