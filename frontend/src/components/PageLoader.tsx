import { Spinner } from "./ui";

export default function PageLoader() {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: 320,
        padding: "4rem 0",
      }}
    >
      <Spinner size={32} />
    </div>
  );
}
