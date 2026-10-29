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
      .fail(function (x) { d.resolve(x.responseJSON || { ok: false, message: "Cannot reach the Java server. Run WebApp.java in Eclipse and open http://localhost:8080 (do not open the .html file directly)." }); });
    return d.promise();
  };

  // Back to top
  $(window).on("scroll", function () { $(this).scrollTop() > 200 ? $("#backToTop").fadeIn(150) : $("#backToTop").fadeOut(150); });
  $("#backToTop").on("click", function () { $("html, body").animate({ scrollTop: 0 }, 400); });

  // Home: carousel auto-slides every 2 seconds
  $("#heroCarousel").each(function () { new bootstrap.Carousel(this, { interval: 2000, ride: "carousel" }); });

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
      window.location.href = r.role === "admin" ? "admin.html" : "enrollment.html";
    });
  });
  $("#registerForm").on("submit", function (e) {
    e.preventDefault();
    var form = this, box = $("#regAlert");
    call("POST", "api/register", { name: $("#fullName").val(), email: $("#email").val(), type: $("#studentType").val(), password: $("#regPass").val() })
      .then(function (r) {
        if (r.ok) { box.removeClass("d-none alert-danger").addClass("alert-success").text(r.message); form.reset(); }
        else { box.removeClass("d-none alert-success").addClass("alert-danger").text(r.message); }
      });
  });

  // Contact (the server checks every field again)
  $("#contactForm").on("submit", function (e) {
    e.preventDefault();
    var form = this;
    $("#cName, #cEmail, #cTopic, #cMessage, #cAgree").removeClass("is-invalid");
    call("POST", "api/contact", {
      name: $("#cName").val(), email: $("#cEmail").val(), topic: $("#cTopic").val(),
      message: $("#cMessage").val(), agree: $("#cAgree").is(":checked"),
      type: $("#contStudent").is(":checked") ? "Continuing student" : "New student"
    }).then(function (r) {
      if (r.ok) { $("#contactAlert").removeClass("d-none"); form.reset(); return; }
      $("#contactAlert").addClass("d-none");
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
      $('a.nav-link[href="login.html"]').text("Logout (" + me.name + ")").attr("href", "#").on("click", logout);
    }
    if ($("#adminPage").length) {
      if (me.role !== "admin") { window.location.href = "login.html"; return; }
      startAdmin();
    }
    if ($("#submitEnroll").length) {
      if (me.role !== "student") { window.location.href = "login.html"; return; }
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
    load();
  }

  // =================== ENROLLMENT ===================
  function startEnrollment() {
    var showMsg = function (text, type) { $("#subjectMessage").html('<div class="alert alert-' + type + '">' + esc(text) + "</div>"); };
    var loadSubjects, loadSelected, loadQueue, loadSlips, pollTimer = null;

    loadSubjects = function () {
      call("GET", "api/subjects", { sort: $("#sortBy").val(), algo: $("#sortAlgo").val() }).then(function (r) {
        if (r.auth === false) { window.location.href = "login.html"; return; }
        $("#termLine").text(r.term);
        $("#maxUnits").text(r.maxUnits); $("#selectedUnits").text(r.units);
        $("#noticeText").html(r.notice ? esc(r.notice) : "No failed subjects. Everything is open to you.");
        $("#sortInfo").text(r.algorithm ? r.algorithm + " used " + r.comparisons + " comparisons." : "Shown in order of code (read from the Binary Search Tree).");
        var html = "";
        r.subjects.forEach(function (s) {
          var picked = s.status === "Selected", blocked = (s.status !== "Available" && !picked) || r.submitted;
          var badge = (s.status === "Available" || picked) ? "success" : "danger";
          html += "<tr><td>" + esc(s.code) + "</td><td>" + esc(s.title) + "</td><td>" + s.units + "</td><td>" + esc(s.section) + "</td><td>" + esc(s.schedule) + "</td><td>" + s.slots +
            '</td><td><span class="badge text-bg-' + badge + '">' + esc(s.status) + '</span></td><td><input class="form-check-input subj" type="checkbox" data-code="' + esc(s.code) + '"' +
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
          html += '<div class="panel mb-3"><strong>Submitted ' + esc(s.submittedAt) + "</strong> &middot; Queue No. " + s.queueNumber + " &middot; " + s.totalUnits + " units<ul class=\"mb-0 mt-2\">";
          s.subjects.forEach(function (x) { html += "<li>" + esc(x.code) + " " + esc(x.title) + " (" + x.units + " units, " + esc(x.schedule) + ")</li>"; });
          html += "</ul></div>";
        });
        $("#slipList").html(html);
      });
    };

    $(document).on("change", ".subj", function () {
      var box = $(this), url = box.is(":checked") ? "api/select" : "api/remove";
      call("POST", url, { code: box.data("code") }).then(function (r) {
        showMsg(r.message, r.ok ? "success" : "danger");
        loadSubjects(); loadSelected();
      });
    });
    $("#sortBy, #sortAlgo").on("change", loadSubjects);
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

    loadSubjects(); loadSelected(); loadQueue(); loadSlips();
  }
});
