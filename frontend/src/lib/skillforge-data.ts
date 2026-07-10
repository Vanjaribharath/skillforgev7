import {
  AlertTriangle,
  Award,
  BookOpen,
  CheckCircle2,
  Clock3,
  FileSpreadsheet,
  MailCheck,
  MonitorDot,
  ShieldCheck,
  TrendingUp,
  UserRoundCheck,
  UsersRound,
} from "lucide-react";

export const organization = {
  name: "Apex Learning Cloud",
  slug: "apex-learning-cloud",
  primaryColor: "#1D4ED8",
  passRate: 78,
  activeCandidates: 184,
  trainerCount: 26,
};

export const dashboardStats = [
  { label: "Assessments this month", value: "142", detail: "+18% from last month", icon: CheckCircle2 },
  { label: "Active candidates", value: "184", detail: "37 in progress now", icon: UserRoundCheck },
  { label: "Approved questions", value: "3,840", detail: "612 waiting review", icon: BookOpen },
  { label: "Average pass rate", value: "78%", detail: "Java stream improved", icon: TrendingUp },
];

export const operationCards = [
  { title: "Email queue", value: "1,248 sent", detail: "12 reminders pending", tone: "green", icon: MailCheck },
  { title: "Integrity alerts", value: "9 review", detail: "No critical incidents", tone: "amber", icon: ShieldCheck },
  { title: "Exports", value: "34 ready", detail: "CSV, Excel, PDF", tone: "blue", icon: FileSpreadsheet },
  { title: "Certificates", value: "318 issued", detail: "Auto verification enabled", tone: "green", icon: Award },
];

export const assessments = [
  {
    id: "java-fresher",
    title: "Java Fresher Certification",
    status: "Scheduled",
    candidates: 126,
    duration: "60 min",
    window: "Today, 10:00-16:00",
    passRate: 74,
    sections: ["OOP", "Collections", "Streams", "Spring Boot"],
  },
  {
    id: "linux-admin",
    title: "Linux Admin Internal Readiness",
    status: "Published",
    candidates: 88,
    duration: "75 min",
    window: "Jul 2, 09:00-18:00",
    passRate: 81,
    sections: ["Linux", "Shell", "Bash", "Troubleshooting"],
  },
  {
    id: "microservices",
    title: "Microservices Practitioner",
    status: "Draft",
    candidates: 0,
    duration: "90 min",
    window: "Not scheduled",
    passRate: 0,
    sections: ["REST API", "Kafka", "Docker", "Kubernetes"],
  },
];

export const questions = [
  { code: "JAVA-0018", subject: "Java", type: "Code Output", difficulty: "Medium", status: "Approved", usage: 18, time: "90s", owner: "Priya S." },
  { code: "SPR-0042", subject: "Spring Boot", type: "Scenario", difficulty: "Hard", status: "Review", usage: 4, time: "180s", owner: "Daniel K." },
  { code: "LIN-0110", subject: "Linux", type: "Multiple Select", difficulty: "Easy", status: "Approved", usage: 32, time: "60s", owner: "Anita R." },
  { code: "KAF-0031", subject: "Kafka", type: "Ordering", difficulty: "Medium", status: "Draft", usage: 0, time: "120s", owner: "Mohan V." },
  { code: "SQL-0099", subject: "PostgreSQL", type: "Fill Blank", difficulty: "Medium", status: "Approved", usage: 11, time: "75s", owner: "Priya S." },
];

export const candidates = [
  { name: "Aarav Mehta", email: "aarav.mehta@example.com", batch: "Java Fresher Q3", department: "Engineering", attempts: 4, trend: "+12%" },
  { name: "Neha Rao", email: "neha.rao@example.com", batch: "Cloud Enablement", department: "Platform", attempts: 3, trend: "+8%" },
  { name: "Ishaan Kapoor", email: "ishaan.kapoor@example.com", batch: "Linux Admin", department: "Operations", attempts: 5, trend: "-3%" },
  { name: "Maya Iyer", email: "maya.iyer@example.com", batch: "API Guild", department: "Integration", attempts: 2, trend: "+19%" },
];

export const liveCandidates = [
  { name: "Aarav Mehta", state: "In Progress", progress: 72, suspicious: 5, device: "Chrome / Windows", time: "18:24" },
  { name: "Neha Rao", state: "Completed", progress: 100, suspicious: 0, device: "Edge / Windows", time: "00:00" },
  { name: "Ishaan Kapoor", state: "Disconnected", progress: 41, suspicious: 20, device: "Chrome / Android", time: "34:10" },
  { name: "Maya Iyer", state: "In Progress", progress: 58, suspicious: 10, device: "Safari / macOS", time: "22:48" },
];

export const reportRows = [
  { label: "Java Fresher Q3", candidates: 126, average: 74, pass: 82, weak: "Streams" },
  { label: "Linux Admin", candidates: 88, average: 81, pass: 86, weak: "Permissions" },
  { label: "API Guild", candidates: 64, average: 69, pass: 71, weak: "OAuth2" },
  { label: "Cloud Enablement", candidates: 112, average: 77, pass: 79, weak: "Kubernetes" },
];

export const candidateQuestions = [
  { number: 1, subject: "Java", prompt: "What is the output of a stream pipeline that maps integers and then calls collect?", state: "Answered" },
  { number: 2, subject: "Spring Boot", prompt: "Choose the correct actuator endpoint behavior for health probes.", state: "Flagged" },
  { number: 3, subject: "Linux", prompt: "Order the commands required to find a process and inspect its open ports.", state: "Current" },
  { number: 4, subject: "SQL", prompt: "Fill the missing clause for grouping records by department.", state: "Unanswered" },
];

export const timeline = [
  { time: "09:00", title: "Java Fresher window opened", icon: Clock3 },
  { time: "09:12", title: "126 invitations delivered", icon: MailCheck },
  { time: "10:05", title: "37 candidates in progress", icon: MonitorDot },
  { time: "10:18", title: "9 integrity events require review", icon: AlertTriangle },
  { time: "10:32", title: "24 certificates generated", icon: Award },
];

export function toneClass(tone: string) {
  if (tone === "green") return "bg-[#ECFDF5] text-green";
  if (tone === "amber") return "bg-[#FFFBEB] text-amber";
  if (tone === "blue") return "bg-[#EFF6FF] text-blue";
  return "bg-[#FEF2F2] text-coral";
}

export function statusClass(status: string) {
  if (status === "Approved" || status === "Completed" || status === "Published" || status === "Scheduled") return "bg-[#ECFDF5] text-green";
  if (status === "Review" || status === "Flagged" || status === "Disconnected") return "bg-[#FFFBEB] text-amber";
  if (status === "Draft" || status === "Current") return "bg-[#EFF6FF] text-blue";
  return "bg-surface text-muted";
}

export const subjects = [
  "Linux",
  "Shell",
  "Java",
  "Spring Boot",
  "SQL",
  "Kafka",
  "Docker",
  "Kubernetes",
  "AWS",
  "JUnit",
  "Mockito",
  "Design Patterns",
];

export const roleCards = [
  { title: "Admin", text: "Control organization setup, users, branding, integrations, health, and audit logs.", icon: UsersRound },
  { title: "Trainer", text: "Create governed questions, reuse templates, schedule assessments, and monitor live cohorts.", icon: BookOpen },
  { title: "Candidate", text: "Open secure links, verify identity, complete tests, resume safely, and download certificates.", icon: UserRoundCheck },
];
